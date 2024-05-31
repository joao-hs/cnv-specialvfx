package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import java.util.SortedSet;
import java.util.TreeSet;


/*
 * Groups of workers that should have the same level of system load.
 */
public class WorkerPool {
    public enum WorkerPoolType {
        LOW, MEDIUM, HIGH, FULL, TERMINATING, SCHRODINGER
    }
    // CPU usage thresholds -- e.g. low is below 30% CPU usage
    public static final double LOW_CPU_USAGE = 0.30;
    public static final double MEDIUM_CPU_USAGE = 0.75;
    public static final double HIGH_CPU_USAGE = 0.9;
    public static final double FULL_CPU_USAGE = 1.0;

    public static final int LOW_CONCURRENT_LOAD = 100;
    public static final int MEDIUM_CONCURRENT_LOAD = 1500;
    public static final int HIGH_CONCURRENT_LOAD = 10000;
    public static final int FULL_CONCURRENT_LOAD = 50000;

    private final WorkerPoolType type;
    private int size = 0;

    private final SortedSet<SupervisedWorker> decreasingCPUWorkers = new TreeSet<>(new SupervisedWorker.DecreasingCPUComparator());
    private final SortedSet<SupervisedWorker> decreasingLoadWorkers = new TreeSet<>(new SupervisedWorker.DecreasingLoadComparator());

    private final Object lock = new Object();

    public WorkerPool(WorkerPoolType type) {
        this.type = type;
    }

    public void addWorker(SupervisedWorker worker) {
        size++;
        synchronized (lock) {
            decreasingCPUWorkers.add(worker);
            decreasingLoadWorkers.add(worker);
        }
    }

    public void removeWorker(SupervisedWorker worker) {
        size--;
        synchronized (lock) {
            decreasingCPUWorkers.remove(worker);
            decreasingLoadWorkers.remove(worker);
        }
    }

    public boolean containsWorker(SupervisedWorker worker) {
        synchronized (lock) {
            return decreasingCPUWorkers.contains(worker);
        }
    }

    public void sendWorkerToPool(SupervisedWorker worker, WorkerPool other) {
        if (this.equals(other)) {
            return;
        }

        if (this.containsWorker(worker)) {
            this.removeWorker(worker);
            other.addWorker(worker);
            System.out.println(String.format(".(WorkerPool) Worker %s moved from %s to %s", worker.getInstance().getPublicIpAddress(), this.type.name(), other.type.name()));
        }
    }

    public int size() {
        return size;
    }

    /* 
     * Returns a worker from the pool that is available to process a request
     */
    public SupervisedWorker getAvailableWorker(int cost) {
        for (SupervisedWorker worker : decreasingLoadWorkers) {
            if (worker.getLoad() + cost < HIGH_CONCURRENT_LOAD) {
                return worker;
            }
        }
        return null;
    }

    public SortedSet<SupervisedWorker> getWorkers() {
        synchronized (lock) {
            return new TreeSet<>(decreasingCPUWorkers);
        }
    }

    public WorkerPoolType getType() {
        return type;
    }
}
