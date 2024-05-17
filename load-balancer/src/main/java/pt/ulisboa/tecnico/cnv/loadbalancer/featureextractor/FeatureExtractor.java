package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.util.ArrayList;
import java.util.Map;

public interface FeatureExtractor {
    /*
     * Extracts the features from the request
     */
    public ArrayList<Integer> extractFeatures(String requestBody, Map<String, String> params);
}