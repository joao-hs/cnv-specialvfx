package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.estimators;

import java.awt.*;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.CostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.LinearRegressor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.ImageProcessingFeatureExtractor;

public class ImageProcessingCostEstimator extends CostEstimator {
    private static final String IMAGE_PROCESSING_MODEL_TABLE_BASE = "image-processing-models";


    public ImageProcessingCostEstimator(Type type, String model) {
        super(type);
        this.setSerializedModelTableName(type);
        this.regressor = LinearRegressor.fromModel(model);
    }

    public ImageProcessingCostEstimator(Type type) {
        super(type);
        this.setSerializedModelTableName(type);
        if (!this.loadModel()) {
            this.regressor = new LinearRegressor(ImageProcessingFeatureExtractor.NUM_FEATURES);
        }
    }

    private void setSerializedModelTableName(Type type) {
        this.SERIALIZED_MODEL_TABLE_NAME = String.format("%s-%s", IMAGE_PROCESSING_MODEL_TABLE_BASE, type.toString());
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
}
