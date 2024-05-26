package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpServer;


import pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.FeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.ImageProcessingFeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.featureextractor.RaytracerFeatureExtractor;
import pt.ulisboa.tecnico.cnv.loadbalancer.handlers.LoadBalancingHandler;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.Supervisor;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.SupervisorImpl;

/*
 * LoadBalancer class is responsible for:
 *  - Reverse proxying incoming requests to the selected server
 */
public class LoadBalancer {

    public static final AtomicLong requestId = new AtomicLong(-1);

    public static void main(String[] args) throws Exception {
        Supervisor supervisor = SupervisorImpl.getInstance();
        FeatureExtractor imageProcessingFeatureExtractor = ImageProcessingFeatureExtractor.getInstance();        
        FeatureExtractor raytracerFeatureExtractor = RaytracerFeatureExtractor.getInstance();

        AutoScaler.getInstance().run();

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new LoadBalancingHandler(null));
        server.createContext("/raytracer", new LoadBalancingHandler(raytracerFeatureExtractor));
        server.createContext("/blurimage", new LoadBalancingHandler(imageProcessingFeatureExtractor));
        server.createContext("/enhanceimage", new LoadBalancingHandler(imageProcessingFeatureExtractor));
        server.start();
    }
}
