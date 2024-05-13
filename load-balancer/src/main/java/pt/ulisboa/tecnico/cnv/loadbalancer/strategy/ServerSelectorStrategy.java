package pt.ulisboa.tecnico.cnv.loadbalancer.strategy;

import java.util.ArrayList;
import java.util.Map;

import com.amazonaws.services.ec2.model.Instance;

public interface ServerSelectorStrategy {
    /*
     * Selects the server to handle the request
     */
    public String selectServer(Map<Instance, Integer> instanceLoad, ArrayList<Integer> requestFeatures);
}
