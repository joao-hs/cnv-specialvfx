package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

public class ImageProcessingFeatureExtractor implements FeatureExtractor {

    private static ImageProcessingFeatureExtractor instance = null;

    public static final int NUM_FEATURES = 1;

    private ImageProcessingFeatureExtractor() {
    }

    public static ImageProcessingFeatureExtractor getInstance() {
        if (instance == null) {
            instance = new ImageProcessingFeatureExtractor();
        }
        return instance;
    }

    @Override
    public ArrayList<Integer> extractFeatures(InputStream bodyStream, Map<String, String> params) {
        String result = new BufferedReader(new InputStreamReader(bodyStream)).lines().collect(Collectors.joining("\n"));
        String[] results = result.split(",");
        byte[] decoded = Base64.getDecoder().decode(results[1]);
        ArrayList<Integer> features = new ArrayList<>(NUM_FEATURES);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            BufferedImage bi = ImageIO.read(bais);
            features.add((int) Math.round(bi.getWidth()*bi.getHeight() / 1e3));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return features;
    }
    
}
