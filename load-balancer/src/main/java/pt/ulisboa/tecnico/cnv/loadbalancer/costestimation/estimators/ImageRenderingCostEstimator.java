package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.estimators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;

import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.CostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.LinearRegressor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.RaytracerFeatureExtractor;

public class ImageRenderingCostEstimator extends CostEstimator {
    private static final int TRAINING_UPDATE_INTERVAL = 60000;
    private static final String TABLE_NAME = "raytracer-scored-requests";
    private static final String IMAGE_RENDERING_MODEL_TABLE = "raytracer-models";
    private Long lastSeenRequestId = -1L;

    LinearRegressor regressor;

    public ImageRenderingCostEstimator(String model) {
        this.SERIALIZED_MODEL_TABLE_NAME = IMAGE_RENDERING_MODEL_TABLE;
        this.regressor = LinearRegressor.fromModel(model);
    }

    public ImageRenderingCostEstimator(int numFeatures) {
        this.SERIALIZED_MODEL_TABLE_NAME = IMAGE_RENDERING_MODEL_TABLE;
        this.regressor = new LinearRegressor(numFeatures);
    }

    public ImageRenderingCostEstimator() {
        this.SERIALIZED_MODEL_TABLE_NAME = IMAGE_RENDERING_MODEL_TABLE;
        this.loadModel();
    }

    @Override
    public int estimateCost(List<Integer> features) {
        return regressor.estimateCost(features);
    }

    @Override
    public void stochasticIncrementalUpdate(Pair<List<Integer>, Integer> row) {
        List<Pair<List<Integer>, Integer>> batch = List.of(row);
        regressor.train(batch);
    }

    @Override
    public void batchIncrementalUpdate(List<Pair<List<Integer>, Integer>> batch) {
        regressor.train(batch);
    }
    
    /*
     * Fetch training data from the database and train the model
     */
    public void onlineTraining() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(TRAINING_UPDATE_INTERVAL);
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
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.GT.toString())
                .withAttributeValueList(new AttributeValue().withN(lastSeenRequestId.toString()));
        scanFilter.put("requestId", condition);

        // Scan the table
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(TABLE_NAME)
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
     TODO (future-work): Better conditions whether to save the model or not.
     */
    public void processFreshData(List<Pair<List<Integer>, Integer>> data) {
        if (data.size() == 0) {
            return;
        }
        boolean updated = false;
        switch (updateType) {
            case STOCHASTIC:
                data.forEach(this::stochasticIncrementalUpdate);
                updated = true;
                break;
            case BATCH:
                this.batchIncrementalUpdate(data);
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
