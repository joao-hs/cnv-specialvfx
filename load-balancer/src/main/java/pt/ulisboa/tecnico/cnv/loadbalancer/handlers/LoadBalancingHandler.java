package pt.ulisboa.tecnico.cnv.loadbalancer.handlers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.CostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.FeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.SupervisedWorker;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.Supervisor;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.SupervisorImpl;

public class LoadBalancingHandler implements HttpHandler {
    private static final int WORKER_PORT = LoadBalancer.WORKER_PORT;

    private static final AWSLambda lambda = AWSLambdaClient.builder()
            .withRegion(Regions.EU_WEST_3)
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .build();

    private final Supervisor supervisor = SupervisorImpl.getInstance();
    private final CostEstimator costEstimator;
    private final FeatureExtractor featureExtractor;

    public LoadBalancingHandler(FeatureExtractor featureExtractor, CostEstimator costEstimator) {
        this.featureExtractor = featureExtractor;
        this.costEstimator = costEstimator;
    }

    /*
     * Copy the request and send it to the selected server, then 
     * gather the response and send it back to the client
     */
    private void forwardRequest(URL destination, long requestId, String method, InputStream bodyStream, HttpExchange exchange, ArrayList<Integer> extractedFeatures) throws IOException, URISyntaxException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        // Create base request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(destination.toURI());

        if ("POST".equals(method)) {
            // Copy request body
            String body = new BufferedReader(new InputStreamReader(bodyStream)).lines().collect(Collectors.joining("\n"));
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));

            // Append custom headers: Request ID and retrieved features
            requestBuilder.header("X-Request-Id", Long.toString(requestId));
            if (extractedFeatures != null) {
                extractedFeatures.stream().map(i -> Integer.toString(i)).forEach(v -> requestBuilder.header("X-Features", v));
            }
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(String.format("Forward Request to [%s] and got response code [%d]", destination.getHost(), response.statusCode()));

        exchange.sendResponseHeaders(response.statusCode(), response.body().length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.body().getBytes());
        os.close();

    }

    private Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        return Stream.of(query.split("&"))
            .map(s -> s.split("="))
            .collect(Collectors.toMap(s -> s[0], s -> s.length > 1 ? s[1] : ""));
    }

    private URL createURLFromOriginal(URI originalURI, String newAddress) throws MalformedURLException {
        String file;
        String query = originalURI.getRawQuery();
        if (query == null) {
            file = originalURI.getRawPath();
        } else {
            file = String.format("%s?%s", originalURI.getRawPath(), query);
        }
        return new URL("http", newAddress, WORKER_PORT, file);
    }

    private Map<String, String> getImageProcLambdaEvent(InputStream bodyStream) {
        Map<String, String> event = new HashMap<>();
        String result = new BufferedReader(new InputStreamReader(bodyStream)).lines().collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];

        event.put("body", resultSplits[1]);
        event.put("format", format);

        return event;
    }

    private Map<String, String> getRaytracerLambdaEvent(HttpExchange exchange, InputStream bodyStream) throws IOException {
        // get request arguments to initialize the event map
        URI requestedUri = exchange.getRequestURI();
        String query = requestedUri.getRawQuery();
        Map<String, String> event = queryToMap(query);

        Map<String, Object> body = new ObjectMapper().readValue(bodyStream, new TypeReference<Map<String, Object>>() {});
        byte[] input = ((String) body.get("scene")).getBytes();
        Base64.Encoder encoder = Base64.getEncoder();
        event.put("input", encoder.encodeToString(input));

        byte[] texmap = null;
        if (body.containsKey("texmap")) {
            // Convert ArrayList<Integer> to byte[]
            ArrayList<Integer> texmapBytes = (ArrayList<Integer>) body.get("texmap");
            texmap = new byte[texmapBytes.size()];
            for (int i = 0; i < texmapBytes.size(); i++) {
                texmap[i] = texmapBytes.get(i).byteValue();
            }
            event.put("texmap", encoder.encodeToString(texmap));
        }

        return event;
    }

    private void forwardWithLambda(InputStream bodyStream, HttpExchange exchange) throws IOException {
        String functionName = null;
        Map<String, String> event;

        if (exchange.getRequestURI().getPath().startsWith("/blurimage") || exchange.getRequestURI().getPath().startsWith("/enhanceimage")) {
            functionName = "imageproc-lambda";
            event = getImageProcLambdaEvent(bodyStream);
        } else if (exchange.getRequestURI().getPath().startsWith("/raytracer")) {
            functionName = "raytracer-lambda";
            event = getRaytracerLambdaEvent(exchange, bodyStream);
        } else {
            return;
        }


        InvokeRequest request = new InvokeRequest()
                .withFunctionName(functionName)
                .withPayload(new Gson().toJson(event));

        InvokeResult response = lambda.invoke(request);
        if (response.getStatusCode() != 200) {
            throw new RuntimeException("Lambda invocation failed");
        }

        String output;
        Charset charset = StandardCharsets.UTF_8;
        if (functionName.equals("imageproc-lambda")) {
            output = String.format("data:image/%s;base64,%s", event.get("format"), charset.decode(response.getPayload()).toString());
        } else {
            output = String.format("data:image/bmp;base64,%s", charset.decode(response.getPayload()).toString());
        }

        try {
            exchange.sendResponseHeaders(200, output.length());
            OutputStream os = exchange.getResponseBody();
            os.write(output.getBytes());
            os.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long requestId = LoadBalancer.requestId.incrementAndGet();
        if (featureExtractor == null) {
            SupervisedWorker picked = supervisor.getBestFitForCost(0);
            if (picked == null) {
                throw new RuntimeException("No instance available to handle request");
            }
            try {
                forwardRequest(
                        createURLFromOriginal(exchange.getRequestURI(), picked.getInstance().getPublicIpAddress()),
                        requestId,
                        exchange.getRequestMethod(),
                        exchange.getRequestBody(),
                        exchange,
                        null
                );
            } catch (URISyntaxException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        Map<String, String> params = queryToMap(exchange.getRequestURI().getRawQuery());
        byte[] requestBody = exchange.getRequestBody().readAllBytes();

        ArrayList<Integer> features = featureExtractor.extractFeatures(new ByteArrayInputStream(requestBody), params);

        SupervisedWorker picked = null;
        String address = null;
        int cost = costEstimator.estimateCost(features);
        System.out.println("Estimated cost: " + cost);

        if (LoadBalancer.LOCALHOST) {
            address = "localhost";
        } else {
            picked = supervisor.getBestFitForCost(cost);
            System.out.println("Picked instance: " + (picked == null ? "lambda" : picked.getInstance().getInstanceId()));
            if (picked != null) {
                address = picked.getInstance().getPublicIpAddress();
                supervisor.registerRequestForWorker(picked, requestId, cost);
            }
        }
        if (picked == null && address == null) {
            // trigger Lambda when supervisor can't find any instance suitable for the request
            if (LoadBalancer.LOCALHOST) {
                throw new RuntimeException("No instance available to handle request");
            }
            forwardWithLambda(new ByteArrayInputStream(requestBody), exchange);
            return;
        }

        try {
            forwardRequest(
                    createURLFromOriginal(exchange.getRequestURI(), address),
                    requestId,
                    exchange.getRequestMethod(),
                    new ByteArrayInputStream(requestBody),
                    exchange,
                    features
            );
        } catch (URISyntaxException | InterruptedException e) {
            // if something goes wrong, trigger Lambda
            if (!LoadBalancer.LOCALHOST) {
                forwardWithLambda(new ByteArrayInputStream(requestBody), exchange);
                System.out.println("Error forwarding request to instance, triggered lambda instead");
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
