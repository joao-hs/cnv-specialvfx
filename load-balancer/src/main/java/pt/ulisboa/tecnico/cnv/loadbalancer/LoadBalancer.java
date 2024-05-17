package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpServer;


import pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.FeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.ImageProcessingFeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.RaytracerFeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.handlers.LoadBalancingHandler;
import pt.ulisboa.tecnico.cnv.loadbalancer.strategy.RandomWorkerSelector;
import pt.ulisboa.tecnico.cnv.loadbalancer.strategy.WorkerSelectorStrategy;

/*
 * LoadBalancer class is responsible for:
 *  - Reverse proxying incoming requests to the selected server
 */
public class LoadBalancer {

    public static final AtomicLong requestId = new AtomicLong(-1);

    public static void main(String[] args) throws Exception {
        WorkerSelectorStrategy workerSelector = RandomWorkerSelector.getInstance();
        FeatureExtractor imageProcessingFeatureExtractor = ImageProcessingFeatureExtractor.getInstance();        
        FeatureExtractor raytracerFeatureExtractor = RaytracerFeatureExtractor.getInstance();

        AutoScaler.getInstance().run();

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new LoadBalancingHandler(workerSelector, null));
        server.createContext("/raytracer", new LoadBalancingHandler(workerSelector, raytracerFeatureExtractor));
        server.createContext("/blurimage", new LoadBalancingHandler(workerSelector, imageProcessingFeatureExtractor));
        server.createContext("/enhanceimage", new LoadBalancingHandler(workerSelector, imageProcessingFeatureExtractor));
        server.start();
    }
}
