package pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


public class RaytracerFeatureExtractor implements FeatureExtractor {
    public static final int NUM_FEATURES = 3;
    
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
    
    private void extractFeaturesFromBody(InputStream bodyStream, ArrayList<Integer> features) {
        // Refer to raytracer's README for the expected format of the scene description
        Map<String, Object> body = null;
        try {
            body = mapper.readValue(bodyStream, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        scanner.nextLine();
        for (int i = 0; i < lights; i++) {
            scanner.nextLine(); // light description
        }

        // pigments
        int pigments = scanner.nextInt();
        scanner.nextLine();
        for (int i = 0; i < pigments; i++) {
            String line = scanner.nextLine(); // pigment description
            if (line.startsWith("texmap")) {
                scanner.nextLine();
                scanner.nextLine();
            }
        }

        // finishes
        int finishes = scanner.nextInt();
        scanner.nextLine();
        for (int i = 0; i < finishes; i++) {
            scanner.nextLine(); // finish description
        }

        // shapes
        int shapes = scanner.nextInt();
        scanner.nextLine();
        for (int i = 0; i < shapes; i++) {
            scanner.nextLine(); // shape description
        }

        scanner.close();

        features.add((lights + pigments + finishes) * shapes);
    
        // texmap
        if (body.containsKey("texmap")) {
            features.add(1);
        } else {
            features.add(0);
        }
    }


    @Override
    public ArrayList<Integer> extractFeatures(InputStream bodyStream, Map<String, String> params) {
        // Refer to raytracer's README for the expected format of the scene description
        ArrayList<Integer> features = new ArrayList<>();

        // Body features
        extractFeaturesFromBody(bodyStream, features);

        // Params features
        if (Boolean.parseBoolean(params.get("aa"))) {
            features.add(1);
        } else {
            features.add(0);
        }

        return features;
    }
    
}
