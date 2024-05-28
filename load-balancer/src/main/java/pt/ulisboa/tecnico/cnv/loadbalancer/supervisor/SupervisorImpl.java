package pt.ulisboa.tecnico.cnv.loadbalancer.supervisor;

import com.amazonaws.services.ec2.model.Instance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

public class SupervisorImpl implements Supervisor {

    private static SupervisorImpl instance = null;
    private Map<Instance, Set<Pair<Long, Integer>>> instRequests= new HashMap<Instance, Set<Pair<Long, Integer>>>();
    private Map<Instance, Set<Pair<Long, Integer>>> instRemoving= new LinkedHashMap<Instance, Set<Pair<Long, Integer>>>();
    private Map<Instance, Pair<Integer, Double>> instAvailableCosts= new LinkedHashMap<Instance, Pair<Integer, Double>>();
    private LinkedList<Pair<Long, Integer>> requestQueue = new LinkedList<Pair<Long, Integer>>();
    private final int HEALTH_INTERVAL = 10000;
    private final int MAX_COST = 16;
    private final double IDEAL_CPU = 0.75;

    private SupervisorImpl() {
        start();
    }

    public static SupervisorImpl getInstance() {
        if (instance == null) {
            instance = new SupervisorImpl();
        }
        return instance;
    }

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
                System.out.println("Checking health of instance: " + inst.toString());
                
                HttpClient client = HttpClient.newHttpClient();
                String url = "http://" + ipAddress + "/health";
                HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(2))
                    .uri(URI.create(url))
                    .GET()
                    .build();
        
                HttpResponse<String> response;
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    removeInactiveInstance(inst);
                    return;
                }

                if (response.statusCode() / 100 != 2) {
                    //unhandled case: the supervisor assumes that in this case the instance is dead
                    //and removes it from every list. Possible problem: incorrect instances are kept alive
                    // doing nothing instead of being killed.
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
                }
                
            }).start();
        }
    }

    public boolean registerActiveInstance(Instance inst) {
        for (int i = 0; i < 180; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            HttpClient client = HttpClient.newHttpClient();
            String url = "http://" + inst.getPublicIpAddress() + "/health";
            HttpRequest request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(1))
                .uri(URI.create(url))
                .GET()
                .build();
    
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                continue;
            }

            if (response.statusCode() / 100 == 2) {
                this.instAvailableCosts.put(inst, Pair.of(0, 0.0));
                this.instRequests.put(inst, new HashSet<Pair<Long, Integer>>());
                return true;
            }
        }
        System.out.println("Register New Instance Failed due to not responding to any healt check after 3 minutes");
        return false;
    }

    @Override
    public void registerRequestForInstance(Instance instance, long requestId, int cost) {
        if (this.instRemoving.containsKey(instance)){
            Set<Pair<Long, Integer>> requests = this.instRemoving.get(instance);
            int instcost = 0;
            if (!requests.isEmpty()){
                for (Pair<Long,Integer> request : requests) {
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
            this.instRequests.put(instance, new HashSet<Pair<Long, Integer>>());
        }
        this.instRequests.get(instance).add(Pair.of(requestId, cost));
        this.instAvailableCosts.put(instance, Pair.of(currentCost + cost, this.instAvailableCosts.get(instance).getRight()));
    }

    public void removeRequestForInstance(Instance instance, long requestId) {
        if (this.instRemoving.containsKey(instance)) {
            Set<Pair<Long, Integer>> requests = this.instRemoving.get(instance);
            Set<Pair<Long, Integer>> tmp = new HashSet<Pair<Long, Integer>>();
            for (Pair<Long,Integer> pair : requests) {
                if (pair.getLeft().equals(requestId)) {
                    tmp.add(pair);
                }
            }
            requests.removeAll(tmp);
            return;
        }
        if (this.instAvailableCosts.containsKey(instance) && this.instRequests.containsKey(instance)) {
            Set<Pair<Long, Integer>> requests = this.instRequests.get(instance);
            Set<Pair<Long, Integer>> tmp = new HashSet<Pair<Long, Integer>>();
            int cost = 0;
            for (Pair<Long,Integer> pair : requests) {
                if (pair.getLeft().equals(requestId)) {
                    tmp.add(pair);
                    cost = cost + pair.getRight();
                }
            }
            requests.removeAll(tmp);
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
        for (Instance inst : this.instAvailableCosts.keySet()) {
            if (this.instAvailableCosts.get(inst).getLeft() + cost <= MAX_COST*IDEAL_CPU &&
                this.instAvailableCosts.get(inst).getRight() <= IDEAL_CPU){
                return inst;
            }
        }
        for (Instance inst : this.instAvailableCosts.keySet()) {
            if (this.instAvailableCosts.get(inst).getLeft() + cost < MAX_COST &&
                this.instAvailableCosts.get(inst).getRight() < 1){
                return inst;
            }
        }
        if (!instRemoving.isEmpty()) {
            Instance toReturn = null;
            for (Instance inst : this.instRemoving.keySet()) {
                Set<Pair<Long, Integer>> requests = this.instRemoving.get(inst);
                int instcost = 0;
                if (requests.isEmpty()){
                    toReturn = inst;
                    continue;
                } 
                for (Pair<Long,Integer> request : requests) {
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

    public void removeInactiveInstance(Instance inst){
        if (this.instAvailableCosts.containsKey(inst) && this.instRequests.containsKey(inst)) {
            Set<Pair<Long, Integer>> requests = this.instRequests.get(inst);
            for (Pair<Long, Integer> request : requests) {
                this.requestQueue.add(request);
            }
            this.instAvailableCosts.remove(inst);
            this.instRequests.remove(inst);
        }
        if (this.instRemoving.containsKey(inst)) {
            Set<Pair<Long, Integer>> requests = this.instRemoving.get(inst);
            for (Pair<Long, Integer> request : requests) {
                this.requestQueue.add(request);
            }
            this.instRemoving.remove(inst);
        }
    }

    public void removeInstance(Instance inst) {
        if (this.instAvailableCosts.containsKey(inst)) {
            this.instAvailableCosts.remove(inst);
        }
        if (this.instRequests.containsKey(inst)){
            Set<Pair<Long, Integer>> requests = this.instRequests.get(inst);
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
}
