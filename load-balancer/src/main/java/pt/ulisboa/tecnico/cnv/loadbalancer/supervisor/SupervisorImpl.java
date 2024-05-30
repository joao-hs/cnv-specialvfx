package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import com.amazonaws.services.ec2.model.Instance;

import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler.AutoScaler;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.WorkerPool.WorkerPoolType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class SupervisorImpl implements Supervisor {
    private static SupervisorImpl instance = null;
    static final int HEALTH_INTERVAL = 5000;
    private static final int SECONDS_TO_WAIT_FOR_STARTUP = 180;
    private static final int SECOND_TOLERANCE_BEFORE_CONFIRMING_DEATH = 30;
    private static final int WORKER_PORT = LoadBalancer.WORKER_PORT;
    
    private final WorkerPool lowPool = new WorkerPool(WorkerPool.WorkerPoolType.LOW);
    private final WorkerPool mediumPool = new WorkerPool(WorkerPool.WorkerPoolType.MEDIUM);
    private final WorkerPool highPool = new WorkerPool(WorkerPool.WorkerPoolType.HIGH);
    private final WorkerPool fullPool = new WorkerPool(WorkerPool.WorkerPoolType.FULL);
    private final WorkerPool terminatingPool = new WorkerPool(WorkerPool.WorkerPoolType.TERMINATING);
    private final WorkerPool schrodingerPool = new WorkerPool(WorkerPoolType.SCHRODINGER); // like the cat
    private final Map<WorkerPoolType, WorkerPool> pools = new HashMap<WorkerPoolType, WorkerPool>() {{
        put(WorkerPoolType.LOW, lowPool);
        put(WorkerPoolType.MEDIUM, mediumPool);
        put(WorkerPoolType.HIGH, highPool);
        put(WorkerPoolType.FULL, fullPool);
        put(WorkerPoolType.TERMINATING, terminatingPool);
        put(WorkerPoolType.SCHRODINGER, schrodingerPool);
    }};
    private final Map<SupervisedWorker, WorkerPool> workers = new HashMap<>();

    private SupervisorImpl() {
    }

    public static SupervisorImpl getInstance() {
        if (instance == null) {
            instance = new SupervisorImpl();
        }
        return instance;
    }

    @Override
    public void start() {
        new Thread(() -> {
            System.out.println("Starting Health checker");
            while (true) {
                try {
                    Thread.sleep(SupervisorImpl.HEALTH_INTERVAL);
                    this.handleHealthCheck();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private HttpResponse<String> healthCheck(String ipAddress, Duration timeout) {
        HttpClient client = HttpClient.newHttpClient();
        String url = "http://" + ipAddress + ":" + WORKER_PORT + "/health";
        HttpRequest request = HttpRequest.newBuilder().timeout(timeout)
            .uri(URI.create(url))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            return null;
        }

        return response;

    }

    private void handleHealthCheck() {
        if (this.workers.isEmpty()) {
            return;
        }

        for (SupervisedWorker worker : this.workers.keySet()) {
            new Thread(() -> {
                HttpResponse<String> response = healthCheck(worker.getInstance().getPublicIpAddress(), Duration.ofSeconds(2));
                
                if (response == null) {
                    System.out.println("Worker " + worker.getInstance().getPublicIpAddress() + " is unreachable. May be dead or with high latency.");
                    unresponsiveWorker(worker);
                    return;
                }

                if (response.statusCode() / 100 != 2) {
                    //unhandled case: the supervisor assumes that in this case the instance is dead
                    //and removes it from every list. Possible problem: incorrect instances are kept alive
                    // doing nothing instead of being killed.
                    System.out.println("Worker " + worker.getInstance().getPublicIpAddress() + " is not responding to health check. Removing it.");
                    unresponsiveWorker(worker);
                } else {
                    String res = response.body();
                    String[] splitRes = res.split(" ");
                    if (!res.startsWith("OK: ") || splitRes.length != 2) {
                        unresponsiveWorker(worker);
                        return;
                    }

                    double cpuUsage = Double.parseDouble(splitRes[1]);
                    worker.updateCpuUsage(cpuUsage);
                    System.out.println("Worker " + worker.getInstance().getPublicIpAddress() + " is healthy. CPU Usage: " + cpuUsage);
                }
                
            }).start();
        }
    }

    private void unresponsiveWorker(SupervisedWorker worker) {
        // move worker away
        WorkerPool originalPool = this.pools.get(worker.getWorkerPoolType());
        originalPool.sendWorkerToPool(worker, this.schrodingerPool);

        // move back in or terminate (opening the box)
        boolean isAlive = false;
        boolean removed = false;
        for (int i = 0; i < SECOND_TOLERANCE_BEFORE_CONFIRMING_DEATH; i++) {
            if (i > SECOND_TOLERANCE_BEFORE_CONFIRMING_DEATH / 10 && !removed) {
                // allow autoscaling to replace the instance
                this.workers.remove(worker);
                removed = true;
            }
            HttpResponse<String> response = healthCheck(worker.getInstance().getPublicIpAddress(), Duration.ofSeconds(5));
            if (response != null && response.statusCode() == 200) {
                isAlive = true;
                break;
            }
        }
        if (isAlive) {
            WorkerPool newPool = this.pools.get(worker.getWorkerPoolType());
            schrodingerPool.sendWorkerToPool(worker, newPool);
            this.workers.put(worker, newPool);

        } else {
            AutoScaler.getInstance().terminateInstance(worker.getInstance());
            removeInactiveWorker(worker);
        }

    }

    public boolean registerActiveInstance(Instance inst) {
        if (inst == null) {
            return false;
        }
        SupervisedWorker worker = new SupervisedWorker(inst);
        if (this.workers.containsKey(worker)) {
            return false;
        }

        for (int i = 0; i < SECONDS_TO_WAIT_FOR_STARTUP; i++) {
            HttpResponse<String> response = healthCheck(worker.getInstance().getPublicIpAddress(), Duration.ofSeconds(1));
            if (response != null && response.statusCode() / 100 == 2) {
                System.out.println("Instance " + worker.getInstance().getPublicIpAddress() + " is responding. Adding to worker pool.");
                this.lowPool.addWorker(worker);
                this.workers.put(worker, lowPool);
                return true;
            }

            System.out.println("Instance " + worker.getInstance().getPublicIpAddress() + " is unreachable. Keep trying for " + (SECONDS_TO_WAIT_FOR_STARTUP - i) + " seconds");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void registerRequestForWorker(SupervisedWorker worker, long requestId, int cost) {
        WorkerPool pool = this.workers.get(worker);
        if (pool == null) {
            throw new RuntimeException("Worker not found in any pool");
        }

        worker.updateLoad(requestId, cost);
        if (pool.getType() == WorkerPool.WorkerPoolType.TERMINATING) {
            WorkerPool newPool = pools.get(worker.getWorkerPoolType());
            pool.sendWorkerToPool(worker, newPool);
            workers.put(worker, newPool);
        }
    }

    /*
     * Balances the load between workers, actively trying to compress the load to the minimum number of workers.
     * Tries to put the cost 
     */
    public SupervisedWorker getBestFitForCost(int cost) {
        // Ideal CPU usage is below HIGH of the CPU capacity, so we compact the load by:

        // Push workers in the MEDIUM pool
        WorkerPool pool = this.pools.get(WorkerPoolType.MEDIUM);
        SupervisedWorker worker = pool.getAvailableWorker(cost);
        if (worker != null) {
            return worker;
        }

        // Push workers in the HIGH pool (must be very few that actually can this load)
        pool = this.pools.get(WorkerPoolType.HIGH);
        worker = pool.getAvailableWorker(cost);
        if (worker != null) {
            return worker;
        }

        // Push workers in the LOW pool (they may be new ones, that responded to the demand)
        pool = this.pools.get(WorkerPoolType.LOW);
        worker = pool.getAvailableWorker(cost);
        if (worker != null) {
            return worker;
        }

        // No workers available, check if there are any workers that should be terminating soon
        pool = this.pools.get(WorkerPoolType.TERMINATING);
        worker = pool.getAvailableWorker(cost);
        if (worker != null) {
            return worker;
        }

        // No worker is available in order to preserve a good load balance, use lambda functions
        // This case will probably happen when waiting for system to scale up
        return null;
    }
    
    public void removeInactiveWorker(SupervisedWorker worker) {
        WorkerPool pool = this.workers.get(worker);
        if (pool == null) {
            throw new RuntimeException("Worker not found in any pool");
        }

        pool.removeWorker(worker);
        this.workers.remove(worker);
    }

    public void toRemoveWorker(SupervisedWorker worker) {
        WorkerPool pool = this.workers.get(worker);
        if (pool == null) {
            throw new RuntimeException("Worker not found in any pool");
        }

        pool.sendWorkerToPool(worker, this.terminatingPool);
        this.workers.put(worker, this.terminatingPool);
    }

    public PriorityQueue<SupervisedWorker> getFreeWorkers() {
        PriorityQueue<SupervisedWorker> queue = new PriorityQueue<>();
        for (SupervisedWorker worker : this.workers.keySet()) {
            if (this.workers.get(worker).getType() != WorkerPoolType.TERMINATING) {
                if (worker.getLoad() == 0) {
                    queue.add(worker);
                }
            }
        }
        return queue;
    }

    public PriorityQueue<SupervisedWorker> getFreeToRemoveWorkers() {
        WorkerPool terminatingPool = this.pools.get(WorkerPoolType.TERMINATING);
        PriorityQueue<SupervisedWorker> queue = new PriorityQueue<>();

        for (SupervisedWorker worker : terminatingPool.getWorkers()) {            
            if (worker.getLoad() == 0) {
                queue.add(worker);
            }
        }

        return queue;
    }

    @Override
    public boolean isExhausted() {
        return this.workers.size() == this.highPool.size() + this.fullPool.size();
    }

    @Override
    public Set<String> getExistingInstancesIds() {
        return this.workers.keySet().stream().map(worker -> worker.getInstance().getInstanceId()).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public Set<SupervisedWorker> getExcessWorkers() {
        Set<SupervisedWorker> lowLoadWorkers =  this.lowPool.getWorkers();
        if (this.workers.size() == this.lowPool.size() + this.terminatingPool.size()) {
            Optional<SupervisedWorker> optWorker = lowLoadWorkers.stream().findAny();
            optWorker.ifPresent(lowLoadWorkers::remove);
        }
        return lowLoadWorkers;
    }
}