package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.RaytracerFeatureExtractor;

public abstract class CostEstimator {
    public enum Type {
        blur,
        enhance,
        raytrace,
    }
    private final Type type;

    public CostEstimator(Type type) {
        this.type = type;
    }

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

    private static final Regions AWS_REGION = Regions.EU_WEST_3;
    private static final int TRAINING_UPDATE_INTERVAL = 30000;
    private static final String MSS_TABLE_NAME = "mss";
    private AmazonDynamoDB dynamoDb;
    protected LinearRegressor regressor;
    private Long lastModelId = -1L;
    protected String SERIALIZED_MODEL_TABLE_NAME;
    
    protected IncrementalUpdateType updateType = IncrementalUpdateType.BATCH;

    private Long lastSeenRequestId = -1L;

    public void initDb() {
        if (dynamoDb == null) {
            dynamoDb = AmazonDynamoDBClientBuilder.standard()
                    .withCredentials(new EnvironmentVariableCredentialsProvider())
                    .withRegion(AWS_REGION)
                    .build();
        }
    }

    public void initTable() {
        if (LoadBalancer.LOCALHOST) {
            return;
        }

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
        String serializedModel = this.regressor.exportModel();

        if (LoadBalancer.LOCALHOST) {
            System.out.println("Cost Estimator for type " + this.type.toString() + " is saving the model to disk");
            File localDb = new File("/tmp/SpecialVFX-local-" + SERIALIZED_MODEL_TABLE_NAME + ".db");
            try {
                localDb.createNewFile();
                FileWriter writer = new FileWriter(localDb, true);
                lastModelId+=1;
                writer.write(String.format("%d|%s\n", lastModelId, serializedModel));
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        HashMap<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        lastModelId += 1;
        item.put("id", new AttributeValue().withN(lastModelId.toString()));
        item.put("model", new AttributeValue().withS(serializedModel));
        
        this.dynamoDb.putItem(new PutItemRequest(this.SERIALIZED_MODEL_TABLE_NAME, item));
    }

    /*
     * Load the model from a persistent storage unit
     */
    public boolean loadModel() {
        if (LoadBalancer.LOCALHOST) {
            File localDb = new File("/tmp/SpecialVFX-local-" + SERIALIZED_MODEL_TABLE_NAME + ".db");
            if (!localDb.exists()) {
                return false;
            }
            try {
                Scanner scanner = new Scanner(localDb);
                String row = null;
                while (scanner.hasNextLine()) {
                    row = scanner.nextLine();
                }
                if (row == null) {
                    return false;
                }
                lastModelId = Long.parseLong(row.split("\\|")[0]);
                String model = row.split("\\|")[1];
                this.regressor = LinearRegressor.fromModel(model);
                return true;
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

        }

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

        scanResult.getItems().sort(
                (item1, item2) -> Integer.parseInt(item2.get("id").getN()) - Integer.parseInt(item1.get("id").getN())
        );

        Map<String, AttributeValue> latestModel = scanResult.getItems().get(0);

        this.lastModelId = Long.parseLong(latestModel.get("id").getN());
        String serializedModel = latestModel.get("model").getS();

        this.regressor = LinearRegressor.fromModel(serializedModel);
        return true;   
    }

    /*
     * Fetch training data from the database and train the model
     */
    public void onlineTraining() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(TRAINING_UPDATE_INTERVAL);
                    System.out.println("Cost Estimator for " + this.type.toString() + " is training now!");
                    processFreshData(fetchFreshData());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /*
     * Fetch scored requests from the database that have not been seen yet, and
     * return them
     */
    public List<Pair<List<Integer>, Integer>> fetchFreshData() {
        // ! Caution, dynamoDb must be initialized before calling this function
        // Create filter
        if (LoadBalancer.LOCALHOST) {
            File localDb = new File("/tmp/SpecialVFX-local-mss.db");
            List<Pair<List<Integer>, Integer>> ret = new ArrayList<>();
            if (!localDb.exists()) {
                return ret;
            }
            try {
                Scanner scanner = new Scanner(localDb);
                String row = null;
                String[] rowSplit = null;
                Long id = lastSeenRequestId;
                Integer cost;
                String[] features;
                while (scanner.hasNextLine()) {
                    // id|type|features|cost
                    row = scanner.nextLine();
                    rowSplit = row.split("\\|");
                    if (this.type.ordinal() != Integer.parseInt(rowSplit[1])) {
                        continue;
                    }
                    id = Long.parseLong(rowSplit[0]);
                    if (id <= lastSeenRequestId) {
                        continue;
                    }
                    features = rowSplit[2].split(",");
                    cost = Integer.parseInt(rowSplit[3]);

                    ret.add(
                            Pair.of(
                                    Stream.of(features).map(Integer::parseInt).collect(Collectors.toList()),
                                    cost
                            )
                    );
                }
                scanner.close();
                lastSeenRequestId = id;
                System.out.println("Cost Estimator for type " + this.type.toString() + " is training on " + ret.size() + " new data samples");
                return ret;
            } catch (IOException e) {
                throw  new RuntimeException(e);
            }
        }

        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.GT.toString())
                .withAttributeValueList(new AttributeValue().withN(lastSeenRequestId.toString()));
        scanFilter.put("requestId", condition);

        // Scan the table
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(MSS_TABLE_NAME)
                .withScanFilter(scanFilter);

        // Process Result
        ScanResult scanResult = this.dynamoDb.scan(scanRequest);
        List<Pair<List<Integer>, Integer>> freshData = new ArrayList<>(scanResult.getCount());
        if (scanResult.getCount() == 0) {
            return freshData;
        }

        scanResult.getItems().forEach(item -> {
            Pair<List<Integer>, Integer> dataPoint;
            List<Integer> features = Stream.of(item.get("features").getS().split(","))
                    .map(Integer::parseInt).filter(f -> f != null).collect(Collectors.toList());
            if (features.size() != RaytracerFeatureExtractor.NUM_FEATURES) {
                return;
            }
            try {
                int cost = Integer.parseInt(item.get("cost").getN());
                dataPoint = Pair.of(features, cost);
                freshData.add(dataPoint);
            } catch (NumberFormatException e) {
                return;
            }
        });

        return freshData;
    }

    /*
     * Triggers incremental training of the model with the fresh data
     TODO (future-work): Better conditions whether to save the model or not. Maybe split train-test data and update if the model is better than the previous one.
     */
    public void processFreshData(List<Pair<List<Integer>, Integer>> data) {
        if (data.isEmpty()) {
            return;
        }
        boolean updated = false;
        switch (updateType) {
            case STOCHASTIC:
                System.out.println("Using Stochastic Incremental Updates for " + data.size() + " new samples");
                System.out.println("Old loss: " + LinearRegressor.loss(data, this.regressor));
                data.forEach(this::stochasticIncrementalUpdate);
                System.out.println("New loss: " + LinearRegressor.loss(data, this.regressor));
                updated = true;
                break;
            case BATCH:
                System.out.println("Using Batch Incremental Update for " + data.size() + " new samples");
                System.out.println("Old loss: " + LinearRegressor.loss(data, this.regressor));
                this.batchIncrementalUpdate(data);
                System.out.println("New loss: " + LinearRegressor.loss(data, this.regressor));
                updated = true;
                break;
            default:
                break;
        }

        if (updated) {
            this.saveModel();
        }
    }
}
