package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.dynamodbv2.util.TableUtils.TableNeverTransitionedToStateException;

public abstract class CostEstimator {
    // interface
    /*
     * Estimate the cost of a request based on the retrieved features
     */
    public abstract int estimateCost(List<Integer> features);

    /*
     * Update the cost estimator parameters based on a scored request
     */
    public abstract void stochasticIncrementalUpdate(Pair<List<Integer>, Integer> row);

    /*
     * Update the cost estimator parameters based on a batch of scored requests
     */
    public abstract void batchIncrementalUpdate(List<Pair<List<Integer>, Integer>> batch);

    // common
    protected enum IncrementalUpdateType {
        STOCHASTIC, BATCH
    }

    protected static final Regions AWS_REGION = Regions.EU_WEST_3;
    protected AmazonDynamoDB dynamoDb;
    protected LinearRegressor regressor;
    protected Long lastModelId = -1L;
    protected String SERIALIZED_MODEL_TABLE_NAME;
    
    protected IncrementalUpdateType updateType = IncrementalUpdateType.STOCHASTIC;

    public void initDb() {
        if (dynamoDb == null) {
            dynamoDb = AmazonDynamoDBClientBuilder.standard()
                    .withCredentials(new EnvironmentVariableCredentialsProvider())
                    .withRegion(AWS_REGION)
                    .build();
        }
    }

    public void initTable() {
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(this.SERIALIZED_MODEL_TABLE_NAME)
                .withKeySchema(new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition().withAttributeName("id").withAttributeType("N"))
                .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(5L).withWriteCapacityUnits(5L));

        TableUtils.createTableIfNotExists(this.dynamoDb, createTableRequest);
        try {
            TableUtils.waitUntilActive(this.dynamoDb, this.SERIALIZED_MODEL_TABLE_NAME);
        } catch (TableNeverTransitionedToStateException | InterruptedException e) {
            e.printStackTrace();
        }
    }

        /*
     * Store the model to a persistent storage unit
     */
    public void saveModel() {
        String serializedModel = regressor.exportModel();

        HashMap<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("id", new AttributeValue().withN(lastModelId.toString()));
        item.put("model", new AttributeValue().withS(serializedModel));
        
        this.dynamoDb.putItem(new PutItemRequest(this.SERIALIZED_MODEL_TABLE_NAME, item));
    }

    /*
     * Load the model from a persistent storage unit
     */
    public boolean loadModel() {
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.GT.toString())
            .withAttributeValueList(new AttributeValue().withN(this.lastModelId.toString()));
        scanFilter.put("id", condition);
        ScanRequest scanRequest = new ScanRequest(this.SERIALIZED_MODEL_TABLE_NAME).withScanFilter(scanFilter);
        ScanResult scanResult = this.dynamoDb.scan(scanRequest);
        
        if (scanResult.getCount() == 0) {
            return false;
        }

        Collections.sort(scanResult.getItems(), (item1, item2) -> {
            return Integer.parseInt(item2.get("id").getN()) - Integer.parseInt(item1.get("id").getN());
        });

        Map<String, AttributeValue> latestModel = scanResult.getItems().get(0);

        this.lastModelId = Long.parseLong(latestModel.get("id").getN());
        String serializedModel = latestModel.get("model").getS();

        this.regressor = LinearRegressor.fromModel(serializedModel);
        return true;   
    }
}
