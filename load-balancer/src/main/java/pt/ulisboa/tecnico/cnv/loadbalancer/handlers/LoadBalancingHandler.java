package pt.ulisboa.tecnico.cnv.loadbalancer.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import jdk.jshell.spi.ExecutionControl;
import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.cnv.loadbalancer.costcalculator.CostCalculator;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.FeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.Supervisor;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.SupervisorImpl;

public class LoadBalancingHandler implements HttpHandler {
    private static final int WORKER_PORT = 8000;

    private Supervisor supervisor;

    private CostCalculator costCalculator;

    private final FeatureExtractor featureExtractor;
    private final AutoScaler autoScaler = AutoScaler.getInstance();

    public LoadBalancingHandler(FeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor;
    }

    /*
     * Copy the request and send it to the selected server, then 
     * gather the response and send it back to the client
     */
    private void forwardRequest(URL selectedWorker, HttpExchange originalExchange, ArrayList<Integer> extractedFeatures) throws IOException {
        try {
            // Create http connection to the selected server
            HttpURLConnection workerConnection = (HttpURLConnection) selectedWorker.openConnection();
            workerConnection.setRequestMethod(originalExchange.getRequestMethod());

            // Copy headers from the original request to the server request
            originalExchange.getRequestHeaders().forEach((key, value) -> {
                workerConnection.setRequestProperty(key, value.get(0));
            });

            // Add custom headers
            String features = extractedFeatures != null ? extractedFeatures.stream().map(Object::toString).collect(Collectors.joining(",")) : null;
            workerConnection.setRequestProperty("X-Request-Id", Long.toString(LoadBalancer.requestId.incrementAndGet()));
            if (features != null) {
                workerConnection.setRequestProperty("X-Features", features);
            }

            // Set the body of the request
            if ("POST".equals(originalExchange.getRequestMethod())) {
                workerConnection.setDoOutput(true);
                workerConnection.getOutputStream().write(originalExchange.getRequestBody().readAllBytes());
            }

            // Get the response from the server
            int responseCode = workerConnection.getResponseCode();
            Map<String, List<String>> responseHeaders = workerConnection.getHeaderFields();
            InputStream responseBody = workerConnection.getInputStream();
            
            // Forward the response to the client
            originalExchange.getResponseHeaders().putAll(responseHeaders);
            originalExchange.sendResponseHeaders(responseCode, workerConnection.getContentLength());

            originalExchange.getResponseBody().write(responseBody.readAllBytes());

            // Close the connection
            workerConnection.disconnect();
        
        } catch (IOException e) {
            e.printStackTrace();
            originalExchange.sendResponseHeaders(500, 0);
        } finally {
            originalExchange.close();
        }

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
        return new URL(originalURI.toURL().getProtocol(), newAddress, WORKER_PORT,
                    String.format("/%s?%s", originalURI.getRawPath(), originalURI.getRawQuery()));
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (featureExtractor == null) {
            Instance picked = supervisor.getBestFitForCost(0);
            forwardRequest(createURLFromOriginal(exchange.getRequestURI(), picked.getPublicIpAddress()), exchange, null);
            return;
        }
        Map<Instance, Integer> loadMap = autoScaler.getLoad();
        Map<String, String> params = queryToMap(exchange.getRequestURI().getRawQuery());
        ArrayList<Integer> features = featureExtractor.extractFeatures(exchange.getRequestBody().readAllBytes().toString(), params);
        int cost = costCalculator.estimateCost(features);
        Instance picked = supervisor.getBestFitForCost(cost);
        if (picked == null) {
            // trigger lambda function
            System.out.println("NOT IMPLEMENTED: Lambda function triggering");
        } else {
            // workers
            forwardRequest(createURLFromOriginal(exchange.getRequestURI(), picked.getPublicIpAddress()), exchange, features);
        }
    }
}