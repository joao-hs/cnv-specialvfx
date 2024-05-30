package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.tuple.Pair;

import com.amazonaws.services.ec2.model.Instance;

/*
 * Wrapper class for 
 */
public class SupervisedWorker {
    public static class DecreasingCPUComparator implements Comparator<SupervisedWorker> {
        @Override
        public int compare(SupervisedWorker o1, SupervisedWorker o2) {
            return Double.compare(o2.getCpuUsage(), o1.getCpuUsage());
        }
    }

    public static class DecreasingLoadComparator implements Comparator<SupervisedWorker> {
        @Override
        public int compare(SupervisedWorker o1, SupervisedWorker o2) {
            return Integer.compare(o2.getLoad(), o1.getLoad());
        }
    }

    public static final int HISTORY_RANGE = 15000;
    private static final int CPU_USAGE_HISTORY_SIZE = HISTORY_RANGE / SupervisorImpl.HEALTH_INTERVAL;

    private final Instance worker;
    private Set<Pair<Long, Integer>> currentLoad = new HashSet<>();
    private ReadWriteLock loadLock = new ReentrantReadWriteLock();
    private double cpuUsage = 0;
    private double[] cpuUsageHistory = new double[CPU_USAGE_HISTORY_SIZE];
    private int cpuUsagePointer = 0;
    private ReadWriteLock cpuUsageLock = new ReentrantReadWriteLock();

    public SupervisedWorker(Instance worker) {
        this.worker = worker;
    }

    public void updateLoad(long requestId, int cost) {
        loadLock.writeLock().lock();
        currentLoad.add(Pair.of(requestId, cost));
        loadLock.writeLock().unlock();
    }

    public int getLoad() {
        loadLock.readLock().lock();
        int load = currentLoad.stream().mapToInt(Pair::getRight).sum();
        loadLock.readLock().unlock();
        return load;
    }

    public void updateCpuUsage(double cpuUsage) {
        cpuUsageLock.writeLock().lock();
        cpuUsageHistory[cpuUsagePointer] = cpuUsage;
        cpuUsagePointer = (cpuUsagePointer + 1) % CPU_USAGE_HISTORY_SIZE;
        // mean of cpu usage history
        double sum = 0;
        for (int i=0; i<CPU_USAGE_HISTORY_SIZE; i++) {
            sum += cpuUsageHistory[i];
        }
        this.cpuUsage = sum / CPU_USAGE_HISTORY_SIZE;
        cpuUsageLock.writeLock().unlock();
    }


    public double getCpuUsage() {
        cpuUsageLock.readLock().lock();
        double cpuUsage = this.cpuUsage;
        cpuUsageLock.readLock().unlock();
        return cpuUsage;
    }

    public Instance getInstance() {
        return worker;
    }

    public WorkerPool.WorkerPoolType getWorkerPoolType() {
        double cpuUsage = getCpuUsage();
        if (cpuUsage <= WorkerPool.LOW_CPU_USAGE) {
            return WorkerPool.WorkerPoolType.LOW;
        } else if (cpuUsage <= WorkerPool.MEDIUM_CPU_USAGE) {
            return WorkerPool.WorkerPoolType.MEDIUM;
        } else if (cpuUsage <= WorkerPool.HIGH_CPU_USAGE) {
            return WorkerPool.WorkerPoolType.HIGH;
        } else {
            return WorkerPool.WorkerPoolType.FULL;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SupervisedWorker other = (SupervisedWorker) obj;
        if (worker == null) {
            return other.worker == null;
        }
        return worker.getInstanceId().equals(other.worker.getInstanceId());
    }
}
