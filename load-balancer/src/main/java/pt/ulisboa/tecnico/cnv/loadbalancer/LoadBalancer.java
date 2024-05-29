package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpServer;


import pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.CostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.estimators.ImageProcessingCostEstimator;
import pt.ulisboa.tecnico.cnv.loadbalancer.costestimation.estimators.ImageRenderingCostEstimator;
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
    public static boolean LOCALHOST = false;
    public static final int WORKER_PORT = 8000;
    public static int LB_PORT = 8000;

    public static final AtomicLong requestId = new AtomicLong(-1);

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if ("--local".equals(args[0])) {
                LoadBalancer.LOCALHOST = true;
                LoadBalancer.LB_PORT = 8080; // worker(s) will be running on port 8000
            }
        }

        Supervisor supervisor = SupervisorImpl.getInstance();
        supervisor.start();

        FeatureExtractor imageProcessingFeatureExtractor = ImageProcessingFeatureExtractor.getInstance();        
        FeatureExtractor raytracerFeatureExtractor = RaytracerFeatureExtractor.getInstance();

        CostEstimator blurJobCostEstimator = new ImageProcessingCostEstimator(CostEstimator.Type.blur);
        CostEstimator enhanceJobCostEstimator = new ImageProcessingCostEstimator(CostEstimator.Type.enhance);
        ImageRenderingCostEstimator raytracerJobCostEstimator = new ImageRenderingCostEstimator();

        blurJobCostEstimator.onlineTraining();
        enhanceJobCostEstimator.onlineTraining();
        raytracerJobCostEstimator.onlineTraining();

        AutoScaler.getInstance().run();

        HttpServer server = HttpServer.create(new InetSocketAddress(LoadBalancer.LB_PORT), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new LoadBalancingHandler(null, null));
        server.createContext("/raytracer", new LoadBalancingHandler(raytracerFeatureExtractor, raytracerJobCostEstimator));
        server.createContext("/blurimage", new LoadBalancingHandler(imageProcessingFeatureExtractor, blurJobCostEstimator));
        server.createContext("/enhanceimage", new LoadBalancingHandler(imageProcessingFeatureExtractor, enhanceJobCostEstimator));
        server.start();
    }
}
