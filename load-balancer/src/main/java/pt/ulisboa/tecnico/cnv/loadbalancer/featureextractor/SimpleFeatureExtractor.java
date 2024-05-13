package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.util.ArrayList;

public class SimpleFeatureExtractor implements FeatureExtractor {

    private static SimpleFeatureExtractor instance = null;

    private SimpleFeatureExtractor() {
    }

    public static SimpleFeatureExtractor getInstance() {
        if (instance == null) {
            instance = new SimpleFeatureExtractor();
        }
        return instance;
    }

    @Override
    public ArrayList<Integer> extractFeatures(String request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractFeatures'");
    }
    
}
