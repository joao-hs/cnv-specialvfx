package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import com.amazonaws.services.ec2.model.Instance;

import java.util.PriorityQueue;

public class SupervisorImpl implements Supervisor {

    private static SupervisorImpl instance = null;

    private SupervisorImpl() {
    }

    public static SupervisorImpl getInstance() {
        if (instance == null) {
            instance = new SupervisorImpl();
        }
        return instance;
    }

    @Override
    public void registerRequestForInstance(Instance instance, int requestId) {
    }

    @Override
    public Instance getBestFitForCost(int cost) {
        return null;
    }

    @Override
    public PriorityQueue<Instance> getFreeInstances() {
        return new PriorityQueue<>();
    }
}
