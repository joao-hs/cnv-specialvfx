package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.apache.commons.lang3.tuple.Pair;

import com.google.gson.Gson;

public class LinearRegressor {
    public static LinearRegressor createInitialCostEstimator(CostEstimator.Type type) {
        switch (type) {
            case blur:
                return new LinearRegressor(new Parameters(
                        new double[]{0.15},
                        0.0,
                        1e-6,
                        0.1
                ));
            case enhance:
                return new LinearRegressor(new Parameters(
                        new double[]{0.1},
                        0.0,
                        1e-6,
                        0.1
                ));
            case raytrace:
                return new LinearRegressor(new Parameters(
                        new double[]{30.0,6.0,6.0},
                        -10,
                        0.001,
                        0.01
                ));
            default:
                throw new RuntimeException("No known type of cost estimator provided");
        }
    }

    public static class Parameters {
        @Expose
        public double[] weights;
        public ReadWriteLock weightLocks = new ReentrantReadWriteLock();
        @Expose
        public double bias;
        @Expose
        public double learningRate;
        @Expose
        public double regularizationFactor;

        public Parameters(double[] weights, double bias, double learningRate, double regularizationFactor) {
            this.weights = weights;
            this.bias = bias;
            this.learningRate = learningRate;
            this.regularizationFactor = regularizationFactor;
        }

        public String export() {
            return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this);
        }

        public static Parameters fromJson(String json) {
            Parameters obj = new Gson().fromJson(json, Parameters.class);
            obj.weightLocks = new ReentrantReadWriteLock();
            return obj;
        }
    }

    private Parameters parameters;

    public LinearRegressor() { }

    private LinearRegressor(Parameters parameters) {
        this.parameters = parameters;
    }

    public LinearRegressor(int numFeatures) {
        double[] weights = new double[numFeatures];
        Arrays.fill(weights, 0);
        this.parameters = new Parameters(weights, 0, 1e-7, 0.1);
    }

    public int estimateCost(List<Integer> features) {
        Lock readLock = parameters.weightLocks.readLock();
        try {
            readLock.lock();
            double cost = parameters.bias;
            for (int i = 0; i < features.size(); i++) {
                cost += parameters.weights[i] * features.get(i);
            }
            return (int) Math.round(cost);
        } finally {
            readLock.unlock();
        }
    }

    public void train(List<Pair<List<Integer>, Integer>> trainingData) {
        Lock writeLock = parameters.weightLocks.writeLock();
        int batchSize = trainingData.size();

        double errorSum = 0;
        double[] weightGradient = new double[parameters.weights.length];
        Arrays.fill(weightGradient, 0);

        // Compute gradients
        for (Pair<List<Integer>, Integer> row : trainingData) {
            List<Integer> features = row.getLeft();
            int cost = row.getRight();
            int prediction = estimateCost(features);
            double error = cost - prediction;

            errorSum += error;
            for (int i = 0; i < features.size(); i++) {
                weightGradient[i] += error * features.get(i);
            }
        }

        for (int i = 0; i < weightGradient.length; i++) {
            weightGradient[i] *= 2.0 / batchSize;
            weightGradient[i] += 2 * this.parameters.regularizationFactor * this.parameters.weights[i];
        }

        // Update weights
        try {
            writeLock.lock();
            this.parameters.bias = this.parameters.bias - this.parameters.learningRate * ((double) 2 / batchSize) * errorSum;
            for (int i = 0; i < this.parameters.weights.length; i++) {
                this.parameters.weights[i] = this.parameters.weights[i] + this.parameters.learningRate * weightGradient[i];
            }
        } finally {
            writeLock.unlock();
        }
    }

    private static double meanSquaredError(List<Pair<List<Integer>, Integer>> trainingData, LinearRegressor regressor) {
        double error = 0;
        for (Pair<List<Integer>, Integer> row : trainingData) {
            double prediction = regressor.estimateCost(row.getLeft());
            error += Math.pow(row.getRight() - prediction, 2);
        }
        return error / trainingData.size();
    }

    public static double loss(List<Pair<List<Integer>, Integer>> data, LinearRegressor regressor) {
        double weightSquaredSum = 0;
        for (double weight : regressor.parameters.weights) {
            weightSquaredSum += Math.pow(weight, 2);
        }
        return meanSquaredError(data, regressor) + regressor.parameters.regularizationFactor * weightSquaredSum;
    }


    public String exportModel() {
        return parameters.export();
    }

    public static LinearRegressor fromModel(String model) {
        LinearRegressor regressor = new LinearRegressor();
        regressor.parameters = Parameters.fromJson(model);
        return regressor;
    }
}
