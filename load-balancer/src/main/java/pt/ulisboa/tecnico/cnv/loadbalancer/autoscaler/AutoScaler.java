package pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler;

import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import com.amazonaws.waiters.WaiterParameters;
import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.SupervisedWorker;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.Supervisor;
import pt.ulisboa.tecnico.cnv.loadbalancer.supervisor.SupervisorImpl;

public class AutoScaler {
    private final static Regions AWS_REGION = Regions.EU_WEST_3;
    private static String AMI_ID;
    private static String KEY_NAME;
    private static String SEC_GROUP_ID;

    private final static int AUTO_SCALER_RATE = 10000;
    private final static int MAX_WAIT_ITERS = 30; // resolves to 30 seconds waiting for instance to be running
    private static AutoScaler instance = null;
    private final AmazonEC2 ec2Client;

    private AutoScaler() {
        this.ec2Client = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");
    }


    public static AutoScaler getInstance() {
        if (instance == null) {
            instance = new AutoScaler();
        }
        return instance;
    }

    private void readEnvironmentVariables() {
        AMI_ID = System.getenv("INSTANCE_AMI_ID");
        KEY_NAME = System.getenv("INSTANCE_KEY_NAME");
        SEC_GROUP_ID = System.getenv("INSTANCE_SEC_GROUP_ID");
        if (AMI_ID == null || KEY_NAME == null || SEC_GROUP_ID == null) {
            throw new RuntimeException("Missing environment variables.");
        }
    }

    public void start() {
        readEnvironmentVariables();
        if (LoadBalancer.LOCALHOST) {
            startLocal();
        } else {
            startAws();
        }
    }

    public synchronized void startLocal() {
        Instance instance = new Instance();
        instance.setPublicIpAddress("localhost");
        Supervisor supervisor = SupervisorImpl.getInstance();
        supervisor.registerActiveInstance(instance);
    }

    public synchronized void startAws() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        // Fetch all instances
        for (Reservation reservation : ec2Client.describeInstances().getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                if (instance.getPublicIpAddress() == null) {
                    continue;
                }
                supervisor.registerActiveInstance(instance);
            }
        } 

        new Thread(() -> {
            while (true) {
                try {
                    this.scaleUpHandler();
                    this.scaleDownHandler();
                    this.terminateInstHandler();
                    Thread.sleep(AUTO_SCALER_RATE);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Instance waitUntilRunning(Instance inst)  {
        if (inst.getState().getCode() == 16) {
            return inst;
        }
        Instance instance = null;
        try {
            boolean running = false;
            int iter = MAX_WAIT_ITERS;
            while (!running && iter > 0) {
                DescribeInstancesRequest describeRequest = new DescribeInstancesRequest();
                describeRequest.getInstanceIds().add(inst.getInstanceId());
                DescribeInstancesResult describeResult = ec2Client.describeInstances(describeRequest);
                Optional<Instance> optInstance = describeResult.getReservations().stream().flatMap(reservation -> reservation.getInstances().stream()).filter(i -> Objects.equals(i.getInstanceId(), inst.getInstanceId())).findFirst();
                if (optInstance.isPresent()) {
                    instance = optInstance.get();
                    InstanceState state = instance.getState();
                    running = state.getCode() == 16; // 16: running
                }
                if (!running) {
                    Thread.sleep(1000); // Wait 1 seconds before checking again
                }
                iter -= 1;
            }
        } catch (InterruptedException ie) {
            System.out.println("No longer waiting for instance " + inst.getInstanceId());
        }
        return instance;
    }

    public void scaleUpHandler() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        if (!supervisor.isExhausted()) {
            return;
        }

        Set<String> existingInstancesIds = supervisor.getExistingInstancesIds();

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
            inst = runInstancesResult.getReservation().getInstances().stream()
                    .filter(i -> !existingInstancesIds.contains(i.getInstanceId()))
                    .filter(i -> i.getState().getCode() == 16 || i.getState().getCode() == 0) // 0: pending or 16: running
                    .collect(Collectors.toList()).get(0);
            inst = waitUntilRunning(inst);
            if (inst != null && inst.getState().getCode() == 16) {  // 16: running
                supervisor.registerActiveInstance(inst);
            }

        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }

    }

    public void scaleDownHandler() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        Set<SupervisedWorker> excessWorkers = supervisor.getExcessWorkers();
        excessWorkers.forEach(supervisor::toRemoveWorker);
    }

    public void terminateInstHandler() {
        Supervisor supervisor = SupervisorImpl.getInstance();
        PriorityQueue<SupervisedWorker> queue = supervisor.getFreeToRemoveWorkers();
        for (SupervisedWorker worker : queue) {
            terminateInstance(worker.getInstance());
            supervisor.removeInactiveWorker(worker);
        }
    }

    public void terminateInstance(Instance instance) {
        if (instance == null) {
            return;
        }
        try {
            System.out.println("Terminating the instance with address: " + instance.getPublicIpAddress());
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(instance.getInstanceId());
            this.ec2Client.terminateInstances(termInstanceReq);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }
}