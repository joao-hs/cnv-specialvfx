package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


public class RaytracerFeatureExtractor implements FeatureExtractor {
    public static final int NUM_FEATURES = 9;
    
    private final ObjectMapper mapper = new ObjectMapper();

    private static RaytracerFeatureExtractor instance = null;

    private RaytracerFeatureExtractor() {
    }

    public static RaytracerFeatureExtractor getInstance() {
        if (instance == null) {
            instance = new RaytracerFeatureExtractor();
        }
        return instance;
    }
    
    private void extractFeaturesFromBody(String requestBody, ArrayList<Integer> features) {
        // Refer to raytracer's README for the expected format of the scene description
        Map<String, Object> body = null;
        try {
            body = mapper.readValue(requestBody, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        byte[] input = ((String) body.get("scene")).getBytes();
        Scanner scanner = new Scanner(new ByteArrayInputStream(input));
        
        // view
        scanner.nextLine(); // eye
        scanner.nextLine(); // center
        scanner.nextLine(); // up
        scanner.nextLine(); // fov

        // lights
        int lights = scanner.nextInt();
        features.add(lights);
        for (int i = 0; i < lights; i++) {
            scanner.nextLine(); // light description
        }

        // pigments
        int pigments = scanner.nextInt();
        features.add(pigments);
        for (int i = 0; i < pigments; i++) {
            scanner.nextLine(); // pigment description
        }

        // finishes
        int finishes = scanner.nextInt();
        features.add(finishes);
        for (int i = 0; i < finishes; i++) {
            scanner.nextLine(); // finish description
        }

        // shapes
        int shapes = scanner.nextInt();
        features.add(shapes);
        for (int i = 0; i < shapes; i++) {
            scanner.nextLine(); // shape description
        }

        scanner.close();
    
        // texmap
        if (body.containsKey("texmap")) {
            features.add(((ArrayList<Integer>) body.get("texmap")).size());
        } else {
            features.add(0);
        }
    }


    @Override
    public ArrayList<Integer> extractFeatures(String requestBody, Map<String, String> params) {
        // Refer to raytracer's README for the expected format of the scene description
        ArrayList<Integer> features = new ArrayList<>();

        // Body features
        extractFeaturesFromBody(requestBody, features);

        // Params features
        features.add(Integer.parseInt(params.get("scols")) * Integer.parseInt(params.get("srows")));
        features.add(Integer.parseInt(params.get("wcols")) * Integer.parseInt(params.get("wrows")));
        if (Boolean.parseBoolean(params.get("aa"))) {
            features.add(1);
        } else {
            features.add(0);
        }
        if (Boolean.parseBoolean(params.get("multi"))) {
            features.add(1);
        } else {
            features.add(0);
        }

        return features;
    }
    
}
