package pt.ulisboa.tecnico.cnv.loadbalancer.costestimation;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.tuple.Pair;

import com.google.gson.Gson;

public class LinearRegressor {
    public static class Parameters {
        public double[] weights;
        public ReadWriteLock weightLocks = new ReentrantReadWriteLock();
        public double bias;
        public double learningRate;
        public double regularizationFactor;

        public Parameters(double[] weights, double bias, double learningRate, double regularizationFactor) {
            this.weights = weights;
            this.bias = bias;
            this.learningRate = learningRate;
            this.regularizationFactor = regularizationFactor;
        }

        public String export() {
            return new Gson().toJson(this);
        }

        public static Parameters fromJson(String json) {
            return new Gson().fromJson(json, Parameters.class);
        }
    }

    private Parameters parameters;

    public LinearRegressor() { }

    public LinearRegressor(int numFeatures) {
        double[] weights = new double[numFeatures];
        for (int i = 0; i < numFeatures; i++) {
            weights[i] = 0;
        }
        this.parameters = new Parameters(weights, 0, 0.01, 0.01);
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

        // Compute gradients
        for (Pair<List<Integer>, Integer> row : trainingData) {
            List<Integer> features = row.getLeft();
            int cost = row.getRight();
            int prediction = estimateCost(features);
            double error = cost - prediction;

            errorSum += error;
            for (int i = 0; i < features.size(); i++) {
                weightGradient[i] += -2 * error * features.get(i) / batchSize + 2 * parameters.regularizationFactor * parameters.weights[i];
            }
        }

        // Update weights
        try {
            writeLock.lock();
            this.parameters.bias = this.parameters.bias - this.parameters.learningRate * (-2/batchSize) * errorSum;
            for (int i = 0; i < this.parameters.weights.length; i++) {
                this.parameters.weights[i] = this.parameters.weights[i] - this.parameters.learningRate * weightGradient[i];
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

    private static double loss(List<Pair<List<Integer>, Integer>> data, LinearRegressor regressor) {
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
