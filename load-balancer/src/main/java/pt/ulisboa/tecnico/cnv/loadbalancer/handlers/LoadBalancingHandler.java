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
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.CostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.FeatureExtractor;
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
    private void forwardRequest(URL destination, String method, InputStream bodyStream, HttpExchange exchange, ArrayList<Integer> extractedFeatures) throws IOException, URISyntaxException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        // Create base request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(destination.toURI());

        if ("POST".equals(method)) {
            // Copy request body
            String body = new BufferedReader(new InputStreamReader(bodyStream)).lines().collect(Collectors.joining("\n"));
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));

            // Append custom headers: Request ID and retrieved features
            requestBuilder.header("X-Request-Id", Long.toString(LoadBalancer.requestId.incrementAndGet()));
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

    private void forwardWithLambda(InputStream bodyStream, HttpExchange exchange) {
        String functionName = null;
        if (exchange.getRequestURI().getPath().startsWith("/blurimage") || exchange.getRequestURI().getPath().startsWith("/enhanceimage")) {
            functionName = "imageproc-lambda";
        } else if (exchange.getRequestURI().getPath().startsWith("/raytracer")) {
            functionName = "raytracer-lambda";
        } else {
            return;
        }

        String json = new BufferedReader(new InputStreamReader(bodyStream)).lines().collect(Collectors.joining("\n"));
        InvokeRequest request = new InvokeRequest()
                .withFunctionName(functionName)
                .withPayload(json);
                
        InvokeResult response = lambda.invoke(request);
        if (response.getStatusCode() != 200) {
            throw new RuntimeException("Lambda invocation failed");
        }

        String responseBody = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(response.getPayload().array()))).lines().collect(Collectors.joining("\n"));
        
        try {
            exchange.sendResponseHeaders(200, responseBody.length());
            OutputStream os = exchange.getResponseBody();
            os.write(responseBody.getBytes());
            os.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (featureExtractor == null) {
            Instance picked = supervisor.getBestFitForCost(0);
            try {
                forwardRequest(
                        createURLFromOriginal(exchange.getRequestURI(), picked.getPublicIpAddress()),
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

        Instance picked = null;
        String address = null;
        int cost = costEstimator.estimateCost(features);
        System.out.println("Estimated cost: " + cost);

        if (LoadBalancer.LOCALHOST) {
            address = "localhost";
        } else {
            picked = supervisor.getBestFitForCost(cost);
            System.out.println("Picked instance: " + (picked == null ? "lambda" : picked.getInstanceId()));
            if (picked != null) {
                address = picked.getPublicIpAddress();
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
                    exchange.getRequestMethod(),
                    new ByteArrayInputStream(requestBody),
                    exchange,
                    features
            );
        } catch (URISyntaxException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
