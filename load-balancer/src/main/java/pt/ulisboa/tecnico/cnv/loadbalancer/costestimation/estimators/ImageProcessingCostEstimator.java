package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.estimators;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.CostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.LinearRegressor;

public class ImageProcessingCostEstimator extends CostEstimator {
    public enum Variation {
        blur,
        enhance,
    }
    private static final String IMAGE_PROCESSING_MODEL_TABLE_BASE = "image-processing-models";
    
    LinearRegressor regressor;

    public ImageProcessingCostEstimator(Variation variation, String model) {
        this.setSerializedModelTableName(variation);
        this.regressor = LinearRegressor.fromModel(model);
    }

    public ImageProcessingCostEstimator(Variation variation) {
        this.setSerializedModelTableName(variation);
        this.loadModel();
    }

    private void setSerializedModelTableName(Variation variation) {
        this.SERIALIZED_MODEL_TABLE_NAME = String.format("%s-%s", IMAGE_PROCESSING_MODEL_TABLE_BASE, variation.toString());
    }

    @Override
    public int estimateCost(List<Integer> features) {
        return regressor.estimateCost(features);
    }

    @Override
    public void stochasticIncrementalUpdate(Pair<List<Integer>, Integer> row) {
        throw new UnsupportedOperationException("ImageProcessingCostEstimator does not support online training");
    }

    @Override
    public void batchIncrementalUpdate(List<Pair<List<Integer>, Integer>> batch) {
        throw new UnsupportedOperationException("ImageProcessingCostEstimator does not support online training");
    }

    @Override
    public void saveModel() {
        // do nothing, since training of image processing linear regressors is done offline and stored in the database already
    }
}
