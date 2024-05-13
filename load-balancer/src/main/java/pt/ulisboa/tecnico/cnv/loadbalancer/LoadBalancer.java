package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.FeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.SimpleFeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.strategy.RoundRobinSelector;
import pt.ulisboa.tecnico.cnv.loadbalancer.strategy.ServerSelectorStrategy;

/*
 * LoadBalancer class is responsible for:
 *  - Reverse proxying incoming requests to the selected server
 */
public class LoadBalancer extends AbstractHandler {

    private static ServerSelectorStrategy serverSelector;
    private static FeatureExtractor featureExtractor;

    @Override
    public void handle(String uri, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        
        
        ArrayList<Integer> requestFeatures = featureExtractor.extractFeatures(request.toString());

        String selectedServer = serverSelector.selectServer(AutoScaler.getInstance().getLoad(), requestFeatures);

        forwardRequest(selectedServer, request, response);
    }

    /*
     * Copy the request and send it to the selected server, then 
     * gather the response and send it back to the client
     */
    private void forwardRequest(String selectedServer, HttpServletRequest originalRequest, HttpServletResponse originalResponse) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(new URI(selectedServer))
                .method(originalRequest.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> {
                    try {
                        return originalRequest.getInputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }));
            
            originalRequest.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                builder.header(headerName, originalRequest.getHeader(headerName));
            });

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            
            originalResponse.setStatus(response.statusCode());
            response.headers().map().forEach((headerName, headerValues) -> {
                headerValues.forEach(headerValue -> {
                    originalResponse.addHeader(headerName, headerValue);
                });
            });

            originalResponse.getWriter().write(response.body());
        } catch (IOException | URISyntaxException | InterruptedException e) {
            e.printStackTrace();
            originalResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }


    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java -cp ... pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer <server selector strategy> <feature extractor strategy>");
            System.exit(1);
        }

        String serverSelectorClassName = args[0];
        String featureExtractorClassName = args[1];

        if (serverSelectorClassName.equals("RoundRobinSelector")) {
            serverSelector = RoundRobinSelector.getInstance();
        } else {
            System.out.println("Invalid server selector strategy");
            System.exit(1);
        }

        if (featureExtractorClassName.equals("SimpleFeatureExtractor")) {
            featureExtractor = SimpleFeatureExtractor.getInstance();
        } else {
            System.out.println("Invalid feature extractor strategy");
            System.exit(1);
        }

        AutoScaler.getInstance().run();

        Server server = new Server(8000);
        server.setHandler(new LoadBalancer());
        server.start();
        server.join();
    }
}
