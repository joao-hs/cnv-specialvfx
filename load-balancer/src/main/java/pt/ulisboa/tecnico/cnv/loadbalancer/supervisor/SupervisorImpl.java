package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import com.amazonaws.services.ec2.model.Instance;

import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang3.tuple.Pair;

public class SupervisorImpl implements Supervisor {
    private static SupervisorImpl instance = null;
    private final int HEALTH_INTERVAL = 5000;
    private final int SECONDS_TO_WAIT_FOR_STARTUP = 180;
    private final int WORKER_PORT = LoadBalancer.WORKER_PORT;
    private final int MAX_COST = 10000;
    private final double IDEAL_CPU = 0.75;
    
    private Map<Instance, Map<Pair<Long, Integer>, Object>> instRequests= new ConcurrentHashMap<>();
    private Map<Instance, Map<Pair<Long, Integer>, Object>> instRemoving= new ConcurrentHashMap<>();
    private Map<Instance, Pair<Integer, Double>> instAvailableCosts= new ConcurrentHashMap<>();
    private CopyOnWriteArrayList<Pair<Long, Integer>> requestQueue = new CopyOnWriteArrayList<>();
    
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
                    Thread.sleep(this.HEALTH_INTERVAL);
                    this.handleHealthCheck();
                    this.updateQueue();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateQueue() {
        if (this.requestQueue.isEmpty()) {
            return;
        }

        List<Pair<Long,Integer>> toRemove = new LinkedList<Pair<Long,Integer>>();
        for (Pair<Long,Integer> pair : requestQueue) {
            Instance inst = this.getBestFitForCost(pair.getRight());
            if (inst == null) {
                continue;
            }
            this.registerRequestForInstance(inst, pair.getLeft(), pair.getRight());
            toRemove.add(pair);
        }
        this.requestQueue.removeAll(toRemove);
        // TODO: Send sign to autoscaler if there are too many pending requests to handle?
    }

    private void handleHealthCheck() {
        if (this.instAvailableCosts.isEmpty()) {
            return;
        } 
        for (Instance inst : this.instAvailableCosts.keySet()) {
            String ipAddress = inst.getPublicIpAddress();
            new Thread(() -> {
                System.out.println("Checking health of instance: " + inst.getPublicIpAddress());
                
                HttpClient client = HttpClient.newHttpClient();
                String url = "http://" + ipAddress + ":" + WORKER_PORT + "/health";
                HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(2))
                    .uri(URI.create(url))
                    .GET()
                    .build();
        
                HttpResponse<String> response;
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    System.out.println("Instance " + inst.getPublicIpAddress() + " is unreachable. Most likely dead.");
                    removeInactiveInstance(inst);
                    return;
                }

                if (response.statusCode() / 100 != 2) {
                    //unhandled case: the supervisor assumes that in this case the instance is dead
                    //and removes it from every list. Possible problem: incorrect instances are kept alive
                    // doing nothing instead of being killed.
                    System.out.println("Instance " + inst.getPublicIpAddress() + " is not responding to health check. Removing it.");
                    removeInactiveInstance(inst);
                }
                else {
                    String res = response.body();
                    String[] splitRes = res.split(" ");
                    if (!res.startsWith("OK: ") || splitRes.length != 2) {
                        removeInactiveInstance(inst);
                        return;
                    }

                    Double cpuUsage = Double.parseDouble(splitRes[1]);
                    Pair<Integer, Double> instPair = this.instAvailableCosts.get(inst);
                    this.instAvailableCosts.put(inst, Pair.of(instPair.getLeft(), cpuUsage));
                    System.out.println("Instance " + inst.getPublicIpAddress() + " is healthy. CPU Usage: " + cpuUsage);
                }
                
            }).start();
        }
    }

    @Override
    public boolean registerActiveInstance(Instance inst) {
        System.out.println("Registering New Instance: " + inst.getPublicIpAddress());
        for (int i = 0; i < SECONDS_TO_WAIT_FOR_STARTUP; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            HttpClient client = HttpClient.newHttpClient();
            String url = "http://" + inst.getPublicIpAddress() + ":" + WORKER_PORT + "/health";
            HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(1))
                .uri(URI.create(url))
                .GET()
                .build();
    
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                System.out.println("Instance " + inst.getPublicIpAddress() + " is unreachable. Keep trying for " + (SECONDS_TO_WAIT_FOR_STARTUP - i) + " seconds");
                continue;
            }

            if (response.statusCode() / 100 == 2) {
                this.instAvailableCosts.put(inst, Pair.of(0, 0.0));
                this.instRequests.put(inst, new ConcurrentHashMap<>());
                System.out.println("Register New Instance Succeeded");
                return true;
            }
        }
        System.out.println("Register New Instance Failed due to not responding to any healt check after 3 minutes");
        return false;
    }

    @Override
    public void registerRequestForInstance(Instance instance, long requestId, int cost) {
        if (this.instRemoving.containsKey(instance)){
            Map<Pair<Long, Integer>, Object> requests = this.instRemoving.get(instance);
            int instcost = 0;
            if (!requests.isEmpty()){
                for (Pair<Long,Integer> request : requests.keySet()) {
                    instcost = instcost + request.getRight();
                }
            } 
            if (instcost + cost >= MAX_COST) {
                this.requestQueue.add(Pair.of(requestId, cost));
                return;
            }
            this.instAvailableCosts.put(instance, Pair.of(instcost, 0.0));
            this.instRequests.put(instance, this.instRemoving.get(instance));
            this.instRemoving.remove(instance);
            return;
        }

        if (!this.instAvailableCosts.containsKey(instance)){
            this.requestQueue.add(Pair.of(requestId, cost));
            return;
        }
        int currentCost = this.instAvailableCosts.get(instance).getLeft();
        if (currentCost + cost > MAX_COST || this.instAvailableCosts.get(instance).getRight() > 1.0){
            this.requestQueue.add(Pair.of(requestId, cost));
            return;
        }
        if (!this.instRequests.containsKey(instance)){
            this.instRequests.put(instance, new ConcurrentHashMap<>());
        }
        this.instRequests.get(instance).put(Pair.of(requestId, cost), null);
        this.instAvailableCosts.put(instance, Pair.of(currentCost + cost, this.instAvailableCosts.get(instance).getRight()));
    }

    @Override
    public void removeRequestForInstance(Instance instance, long requestId) {
        if (this.instRemoving.containsKey(instance)) {
            Map<Pair<Long, Integer>, Object> requests = this.instRemoving.get(instance);
            Set<Pair<Long, Integer>> tmp = new HashSet<Pair<Long, Integer>>();
            for (Pair<Long,Integer> pair : requests.keySet()) {
                if (pair.getLeft().equals(requestId)) {
                    tmp.add(pair);
                }
            }
            tmp.forEach(requests::remove);
            return;
        }
        if (this.instAvailableCosts.containsKey(instance) && this.instRequests.containsKey(instance)) {
            Map<Pair<Long, Integer>, Object> requests = this.instRequests.get(instance);
            Pair<Long, Integer> tmp = null;
            int cost = 0;
            for (Pair<Long,Integer> pair : requests.keySet()) {
                if (pair.getLeft().equals(requestId)) {
                    tmp = pair;
                    cost = pair.getRight();
                    break;
                }
            }
            requests.remove(tmp);
            Pair<Integer, Double> infoPair = this.instAvailableCosts.get(instance);
            this.instAvailableCosts.put(instance, Pair.of(infoPair.getLeft() - cost, infoPair.getRight()));
        }
    }

    
    //This function will first try to fill every available instance to 75% of their CPU capacity, 
    // then fill those same instances to 100% and only after that will try to fetch a instance that 
    // was being prepared to be removed
    @Override
    public Instance getBestFitForCost(int cost) {
        if (this.instAvailableCosts.isEmpty()) {
            return null;
        }

        Comparator<Instance> instanceHighestToLowestCPUComparator =
                (o1, o2) -> Double.compare(instAvailableCosts.get(o2).getRight(), instAvailableCosts.get(o1).getRight());

        SortedSet<Instance> sortedInstances = new TreeSet<>(instanceHighestToLowestCPUComparator);
        sortedInstances.addAll(this.instAvailableCosts.keySet());

        for (Instance inst : sortedInstances) {
            if (this.instAvailableCosts.get(inst).getLeft() + cost <= MAX_COST*IDEAL_CPU &&
                this.instAvailableCosts.get(inst).getRight() < 1.0){
                return inst;
            }
        }
        for (Instance inst : sortedInstances) {
            if (this.instAvailableCosts.get(inst).getLeft() + cost < MAX_COST &&
                this.instAvailableCosts.get(inst).getRight() < 1.0){
                return inst;
            }
        }
        if (!instRemoving.isEmpty()) {
            Instance toReturn = null;
            for (Instance inst : this.instRemoving.keySet()) {
                Map<Pair<Long, Integer>, Object> requests = this.instRemoving.get(inst);
                int instcost = 0;
                if (requests.isEmpty()){
                    toReturn = inst;
                    continue;
                } 
                for (Pair<Long,Integer> request : requests.keySet()) {
                    instcost = instcost + request.getRight();
                }
                if (instcost + cost < MAX_COST) {
                    return inst;
                }
            }
            return toReturn;
        }
        return null;
    }

    @Override
    public void removeInactiveInstance(Instance inst){
        if (this.instAvailableCosts.containsKey(inst) && this.instRequests.containsKey(inst)) {
            Map<Pair<Long, Integer>, Object> requests = this.instRequests.get(inst);
            this.requestQueue.addAll(requests.keySet());
            this.instAvailableCosts.remove(inst);
            this.instRequests.remove(inst);
        }
        if (this.instRemoving.containsKey(inst)) {
            Map<Pair<Long, Integer>, Object> requests = this.instRemoving.get(inst);
            this.requestQueue.addAll(requests.keySet());
            this.instRemoving.remove(inst);
        }
    }

    @Override
    public void toRemoveInstance(Instance inst) {
        this.instAvailableCosts.remove(inst);
        if (this.instRequests.containsKey(inst)){
            Map<Pair<Long, Integer>, Object> requests = this.instRequests.get(inst);
            this.instRemoving.put(inst, requests);
            this.instRequests.remove(inst);
        }
    }

    @Override
    public PriorityQueue<Instance> getFreeInstances() {
        PriorityQueue<Instance> queue = new PriorityQueue<>();
        for (Instance inst : this.instRequests.keySet()) {
            if (this.instRequests.get(inst).isEmpty()) {
                queue.add(inst);
            }
        }
        return queue;
    }

    @Override
    public PriorityQueue<Instance> getFreeToRemoveInstances() {
        PriorityQueue<Instance> queue = new PriorityQueue<>();
        for (Instance inst : this.instRemoving.keySet()) {
            if (this.instRemoving.get(inst).isEmpty()) {
                queue.add(inst);
            }
        }
        return queue;
    }

    @Override
    public PriorityQueue<Instance> getAllAvailableInstances() {
        PriorityQueue<Instance> queue = new PriorityQueue<>();
        for (Instance inst : this.instAvailableCosts.keySet()) {
            queue.add(inst);
        }
        return queue;
    }

    @Override
    public PriorityQueue<Instance> getAllToRemoveInstances() {
        PriorityQueue<Instance> queue = new PriorityQueue<>();
        for (Instance inst : this.instRemoving.keySet()) {
            queue.add(inst);
        }
        return queue;
    }

    @Override
    public Set<Pair<Instance, Double>> getCpuUsageInstances() {
        Set<Pair<Instance, Double>> cpuInst = new HashSet<>();
        for (Instance inst : this.instAvailableCosts.keySet()) {
            cpuInst.add(Pair.of(inst, this.instAvailableCosts.get(inst).getRight()));
        }
        return cpuInst;
    }

    @Override
    public Set<Instance> areAllAvailableInstFull() {
        Set<Instance> instances = new HashSet<>();
        for (Instance inst : this.instAvailableCosts.keySet()) {
            if (this.instAvailableCosts.get(inst).getLeft() < IDEAL_CPU*MAX_COST ||
                this.instAvailableCosts.get(inst).getRight() < IDEAL_CPU ) {
                return null;
            }
            instances.add(inst);
        }
        if (!this.instRemoving.isEmpty()) {
            return null;
        }
        return instances;
    }

    @Override
    public Set<Instance> possibleUnusedInst() {
        Set<Instance> instances = new HashSet<>();
        for (Instance inst : this.instAvailableCosts.keySet()) {
            if (this.instAvailableCosts.get(inst).getLeft() < MAX_COST*(1 - IDEAL_CPU) || 
                this.instAvailableCosts.get(inst).getRight() < (1 - IDEAL_CPU)) {
                instances.add(inst);
            }
        }
        return instances;
    }

}
