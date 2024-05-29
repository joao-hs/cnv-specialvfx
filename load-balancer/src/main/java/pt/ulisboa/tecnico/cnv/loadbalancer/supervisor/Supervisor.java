package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import com.amazonaws.services.ec2.model.Instance;

import java.util.PriorityQueue;

/**
 * Implementations of this interface should be responsible for mapping all active instances to current load. Also
 * implements the balancing algorithm.
 */
public interface Supervisor {
    /**
     * @param instance instance that is going to handle the request
     * @param requestId request unique ID
     */
    public void registerRequestForInstance(Instance instance, long requestId, int cost);

    /**
     * @param cost cost/complexity score of the request that is going to be handled
     * @return the instance that is best fitted to handle the request with the given cost. Or null, if there is no
     * active instance that was deemed fit to handle the request.
     */
    public Instance getBestFitForCost(int cost);

    /**
     * @return priority queue of instances that are request free, higher priority for instances that had a longer
     * request-free period
     */
    public PriorityQueue<Instance> getFreeInstances();

    /*
     * Start the supervisor
     */
    public void start();

    public boolean registerActiveInstance(Instance inst);
}
