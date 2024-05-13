package pt.ulisboa.tecnico.cnv.loadbalancer.strategy;

import java.util.ArrayList;
import java.util.Map;

import com.amazonaws.services.ec2.model.Instance;


public class RoundRobinSelector implements ServerSelectorStrategy {

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
    public String selectServer(Map<Instance, Integer> instanceLoad, ArrayList<Integer> requestFeatures) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'selectServer'");
    }
}