package pt.ulisboa.tecnico.cnv.loadbalancer.strategy;

import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import com.amazonaws.services.ec2.model.Instance;


public class RoundRobinSelector implements WorkerSelectorStrategy {

    private static RoundRobinSelector instance = null;

    private RoundRobinSelector() {
    }

    public static RoundRobinSelector getInstance() {
        if (instance == null) {
            instance = new RoundRobinSelector();
        }
        return instance;
    }

    @Override
    public URL selectWorker(Map<Instance, Integer> instanceLoad, ArrayList<Integer> requestFeatures) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'selectWorker'");
    }
}