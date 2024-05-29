package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.estimators;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.CostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.LinearRegressor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.RaytracerFeatureExtractor;

public class ImageRenderingCostEstimator extends CostEstimator {
    private static final String IMAGE_RENDERING_MODEL_TABLE = "raytracer-models";

    public ImageRenderingCostEstimator(String model) {
        super(Type.raytrace);
        this.SERIALIZED_MODEL_TABLE_NAME = IMAGE_RENDERING_MODEL_TABLE;
        this.regressor = LinearRegressor.fromModel(model);
    }

    public ImageRenderingCostEstimator() {
        super(Type.raytrace);
        this.SERIALIZED_MODEL_TABLE_NAME = IMAGE_RENDERING_MODEL_TABLE;
        if (!this.loadModel()) {
            this.regressor = new LinearRegressor(RaytracerFeatureExtractor.NUM_FEATURES);
        }
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
