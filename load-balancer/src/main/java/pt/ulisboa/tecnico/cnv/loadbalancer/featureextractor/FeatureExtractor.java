package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.util.ArrayList;

public interface FeatureExtractor {
    /*
     * Extracts the features from the request
     */
    public ArrayList<Integer> extractFeatures(String request);
}