package pt.ulisboa.tecnico.cnv.loadbalancer.strategy;

import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import com.amazonaws.services.ec2.model.Instance;

public class RandomWorkerSelector implements WorkerSelectorStrategy {

    private static RandomWorkerSelector instance = null;

    private RandomWorkerSelector() {
    }

    public static RandomWorkerSelector getInstance() {
        if (instance == null) {
            instance = new RandomWorkerSelector();
        }
        return instance;
    }

    @Override
    public URL selectWorker(Map<Instance, Integer> instanceLoad, ArrayList<Integer> requestFeatures) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'selectWorker'");
    }

}