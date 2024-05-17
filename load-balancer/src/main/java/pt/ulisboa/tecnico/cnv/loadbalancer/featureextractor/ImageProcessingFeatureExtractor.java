package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

public class ImageProcessingFeatureExtractor implements FeatureExtractor {

    private static ImageProcessingFeatureExtractor instance = null;

    public static final int NUM_FEATURES = 2;

    private ImageProcessingFeatureExtractor() {
    }

    public static ImageProcessingFeatureExtractor getInstance() {
        if (instance == null) {
            instance = new ImageProcessingFeatureExtractor();
        }
        return instance;
    }

    @Override
    public ArrayList<Integer> extractFeatures(String requestBody, Map<String, String> params) {
        String[] results = requestBody.split(",");
        byte[] decoded = Base64.getDecoder().decode(results[1]);
        ArrayList<Integer> features = new ArrayList<>(NUM_FEATURES);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            BufferedImage bi = ImageIO.read(bais);
            features.add(bi.getWidth()*bi.getHeight());
            features.add(bi.getColorModel().getPixelSize());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return features;
    }
    
}
