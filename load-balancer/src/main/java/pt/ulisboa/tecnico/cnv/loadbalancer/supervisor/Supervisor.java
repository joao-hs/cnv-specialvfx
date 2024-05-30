package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import com.amazonaws.services.ec2.model.Instance;

import java.util.PriorityQueue;
import java.util.Set;

/**
 * Implementations of this interface should be responsible for mapping all active instances to current load. Also
 * implements the balancing algorithm.
 */
public interface Supervisor {
     /*
     * Start the supervisor
     */
    public void start();
    
    //! To be called by the handler:

    /**
     * @param instance instance that is going to handle the request
     * @param requestId request unique ID
     */
    public void registerRequestForWorker(SupervisedWorker instance, long requestId, int cost);

    /**
     * @param cost cost/complexity score of the request that is going to be handled
     * @return the instance that is best fitted to handle the request with the given cost. Or null, if there is no
     * active instance that was deemed fit to handle the request.
     */
    public SupervisedWorker getBestFitForCost(int cost);

    //! To be called by the auto scaler:

    /**
     * Blocking call that waits for the new instance to be responding to health checks. Used when scalling-up.
     * @param inst instance that is going to be registered as active
     * @return true if the instance was successfully registered, false otherwise
     */
    public boolean registerActiveInstance(Instance inst);

    /**
     * When there are too many instances, this method should be called to set aside an instance to be removed.
     ** This won't prevent the instance from completing the ongoing requests nor to be assigned new requests.
     ** Just changes how the supervisor will balance requests to it/away from it.
     * @param worker instance that is going to be set aside to be removed
     */
    public void toRemoveWorker(SupervisedWorker worker);

    /**
     * When the auto scaler decides to terminate an instance, it should inform supervisor to not account for it anymore.
     * @param worker instance that is going to be terminated.
     */
    public void removeInactiveWorker(SupervisedWorker worker);

    /**
     * @return priority queue of instances that are request free, higher priority for instances that had a longer
     * request-free period
     */
    public PriorityQueue<SupervisedWorker> getFreeWorkers();

    /**
     * @return priority queue of instances that are request free and are set aside to be removed
     */
    public PriorityQueue<SupervisedWorker> getFreeToRemoveWorkers();

    /**
     * @return true if all instances that are either at high or full capacity
     */
    public boolean isExhausted();

    /**
     * @return set of instances Ids
     */
    public Set<String> getExistingInstancesIds();

    /**
     * @return set of excessive workers (it will never include all instances)
     */
    public Set<SupervisedWorker> getExcessWorkers();
}
