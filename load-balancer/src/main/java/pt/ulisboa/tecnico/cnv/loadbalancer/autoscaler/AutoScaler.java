package pt.ulisboa.tecnico.cnv.loadbalancer.autoscaler;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

public class AutoScaler {
    private final static String AWS_REGION = "eu-west-3";
    private final static long AUTOSCALING_PERIOD = 10000;
    private final static long INSTANCE_RECOGNITION_PERIOD = 10000;
    private final static long INSTANCE_METRICS_PERIOD = 10000;
    private final static long INSTANCE_METRICS_DELTA = 60000;

    private static AutoScaler instance = null;
    private Map<Instance, Integer> instanceLoad;
    private Dimension instanceDimension;
    private AmazonEC2 ec2Client;
    private AmazonCloudWatch cloudWatchClient;

    private AutoScaler() {
    }


    public static AutoScaler getInstance() {
        if (instance == null) {
            instance = new AutoScaler();
            instance.instanceLoad = new ConcurrentHashMap<>();
            instance.ec2Client = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
            instance.cloudWatchClient = AmazonCloudWatchClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();
            instance.instanceDimension = new Dimension();
            instance.instanceDimension.setName("InstanceId");
        }
        return instance;
    }

    public synchronized void run() {
        // Fetch all instances
        for (Reservation reservation : ec2Client.describeInstances().getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                instanceLoad.put(instance, -1);
            }
        } 

        // Periodically update the list of instances
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(INSTANCE_RECOGNITION_PERIOD);
                    for (Reservation reservation : ec2Client.describeInstances().getReservations()) {
                        for (Instance instance : reservation.getInstances()) {
                            if (!instanceLoad.containsKey(instance)) {
                                instanceLoad.put(instance, -1);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Periodically update the load of each instance
        new Thread(() -> {
            GetMetricStatisticsRequest baseRequest = new GetMetricStatisticsRequest()
                    .withNamespace("AWS/EC2")
                    .withPeriod(60)
                    .withMetricName("CPUUtilization")
                    .withStatistics("Average");

            while (true) {
                try {
                    Thread.sleep(INSTANCE_METRICS_PERIOD);
                    for (Instance instance : instanceLoad.keySet()) {
                        if ("running".equals(instance.getState().getName())) {
                            instanceDimension.setValue(instance.getInstanceId());
                            GetMetricStatisticsRequest request = baseRequest.clone()
                                    .withStartTime(new Date(new Date().getTime() - INSTANCE_METRICS_DELTA))
                                    .withDimensions(instanceDimension)
                                    .withEndTime(new Date());
                            for (Datapoint dp : cloudWatchClient.getMetricStatistics(request).getDatapoints()) {
                                instanceLoad.put(instance, dp.getAverage().intValue());
                            }
                        } else {
                            instanceLoad.put(instance, -1);
                        }
                    }
                } catch (AmazonServiceException ase) {
                    System.out.println("Caught Exception: " + ase.getMessage());
                    System.out.println("Reponse Status Code: " + ase.getStatusCode());
                    System.out.println("Error Code: " + ase.getErrorCode());
                    System.out.println("Request ID: " + ase.getRequestId());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        ).start();

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(AUTOSCALING_PERIOD);
                    // TODO: Implement the autoscaling logic
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public Map<Instance, Integer> getLoad() {
        return instanceLoad;
    }
}