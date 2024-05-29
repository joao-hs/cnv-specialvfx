package pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.Supervisor;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.SupervisorImpl;

public class AutoScaler {
    private final static Regions AWS_REGION = Regions.EU_WEST_3;
    private static String AMI_ID = " "; //TODO
    private static String KEY_NAME = " "; //TODO
    private static String SEC_GROUP_ID = " "; //TODO

    private final static int AUTO_SCALER_RATE = 10000;
    private final static int LOOK_BACK_TIME = 30000;
    private final static int NUM_TO_REMOVE = LOOK_BACK_TIME / AUTO_SCALER_RATE;

    private static AutoScaler instance = null;
    private Dimension instanceDimension;
    private AmazonEC2 ec2Client;
    
    private Map<Instance, Integer> possibleInstToRemove = new ConcurrentHashMap<Instance, Integer>();
    private Set<Instance> lastScaleUpDecision = new HashSet<Instance>();

    private AutoScaler() {
        this.ec2Client = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        this.instanceDimension = new Dimension();
        this.instanceDimension.setName("InstanceId");
    }


    public static AutoScaler getInstance() {
        if (instance == null) {
            instance = new AutoScaler();
        }
        return instance;
    }

    public void run() {
        if (LoadBalancer.LOCALHOST) {
            runLocal();
        } else {
            runAws();
        }
    }

    public synchronized void runLocal() {
        Instance instance = new Instance();
        instance.setPublicIpAddress("localhost");
        Supervisor supervisor = SupervisorImpl.getInstance();
        supervisor.registerActiveInstance(instance);
    }

    public synchronized void runAws() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        // Fetch all instances
        for (Reservation reservation : ec2Client.describeInstances().getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                supervisor.registerActiveInstance(instance);
            }
        } 

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(AUTO_SCALER_RATE);
                    this.scaleUpHandler();
                    this.scaleDownHandler();
                    this.terminateInstHandler();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void scaleUpHandler() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        Set<Instance> scaupDecis = supervisor.areAllAvailableInstFull();
        if (scaupDecis == null) {
            return;
        }
        if(this.lastScaleUpDecision.size() == scaupDecis.size()) {
            boolean isSame = true;
            for (Instance instance : scaupDecis) {
                if (!this.lastScaleUpDecision.contains(instance)) {
                    isSame = false;
                    break;
                }
            }
            if (isSame) {
                return;
            }
        }

        Set<Instance> tmp = this.lastScaleUpDecision;
        this.lastScaleUpDecision = scaupDecis;
        Instance inst = null;
        try {
            System.out.println("Starting a new instance.");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId(AMI_ID)
                               .withInstanceType("t2.micro")
                               .withMinCount(1)
                               .withMaxCount(1)
                               .withKeyName(KEY_NAME)
                               .withSecurityGroupIds(SEC_GROUP_ID);
            RunInstancesResult runInstancesResult = this.ec2Client.runInstances(runInstancesRequest);
            inst = runInstancesResult.getReservation().getInstances().get(0);
               
        } catch (AmazonServiceException ase) {
                this.lastScaleUpDecision = tmp;
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
                return;
        }
        if(inst == null || !supervisor.registerActiveInstance(inst)) {
            this.lastScaleUpDecision = tmp;
            return;
        }
    }

    public void scaleDownHandler() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        Set<Instance> possibleUnusedInst = supervisor.possibleUnusedInst();
        for (Instance inst : possibleUnusedInst) {
            if (!this.possibleInstToRemove.containsKey(inst)){
                this.possibleInstToRemove.put(inst, 1);
                continue;
            }
            this.possibleInstToRemove.put(inst, this.possibleInstToRemove.get(inst) + 1);
        }
        Set<Instance> toDelete = new HashSet<>();
        for (Instance inst : this.possibleInstToRemove.keySet()) {
            int numToRem = this.possibleInstToRemove.get(inst);
            if (!possibleUnusedInst.contains(inst)){
                if (numToRem - 1 == 0) {
                    toDelete.add(inst);
                    continue;
                }
                this.possibleInstToRemove.put(inst, numToRem - 1);
            }
            else if (numToRem >= NUM_TO_REMOVE) {
                supervisor.toRemoveInstance(inst);
                toDelete.add(inst);
            }
        }
        for (Instance inst : toDelete) {
            this.possibleInstToRemove.remove(inst);
        }
    }

    public void terminateInstHandler() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        PriorityQueue<Instance> queue = supervisor.getFreeToRemoveInstances();
        for (Instance instance : queue) {
            try {
                System.out.println("Terminating the instance.");
                TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
                termInstanceReq.withInstanceIds(instance.getInstanceId());
                this.ec2Client.terminateInstances(termInstanceReq);            
            } catch (AmazonServiceException ase) {
                    System.out.println("Caught Exception: " + ase.getMessage());
                    System.out.println("Reponse Status Code: " + ase.getStatusCode());
                    System.out.println("Error Code: " + ase.getErrorCode());
                    System.out.println("Request ID: " + ase.getRequestId());
                    continue;
            }
            supervisor.removeInactiveInstance(instance);
        }
    }
}