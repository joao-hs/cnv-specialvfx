package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;

public interface FeatureExtractor {
    /*
     * Extracts the features from the request
     */
    public ArrayList<Integer> extractFeatures(InputStream bodyStream, Map<String, String> params);
}