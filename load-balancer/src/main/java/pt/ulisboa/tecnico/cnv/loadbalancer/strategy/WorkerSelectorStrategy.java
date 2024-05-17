package pt.ulisboa.tecnico.cnv.loadbalancer.strategy;

import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import com.amazonaws.services.ec2.model.Instance;

public interface WorkerSelectorStrategy {
    /*
     * Selects the server to handle the request
     */
    public URL selectWorker(Map<Instance, Integer> instanceLoad, ArrayList<Integer> requestFeatures);
}
