package dsp1.LocalApplication;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.core.sync.RequestBody; 
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.nio.file.Paths;
import java.io.File;

import java.util.ArrayList;
import java.util.List;


import java.io.File; 

import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;

public class LocalApplication{
    
    //args
    private static String inputFilePath;
    private static String outputFileName;
    private static int workersToFileRation;
    private static boolean terminate;
    //AWS clients
    private static final Region region = Region.US_EAST_1; //TODO
    private static final String MANAGER_TAG_KEY = "Role";
    private static final String MANAGER_TAG_VALUE = "Manager";
    private final Ec2Client ec2 = Ec2Client.builder()
            .region(region)
            .build();
    //NOTE: temporary
    
    private final S3Client s3 = S3Client.builder()
            .region(region)
            .build();
    
    private final SqsClient LocalManagerSQS = SqsClient.builder()
            .region(region)
            .build();


    public void main(String args[]) {
        this.inputFilePath = args[0];
        this.outputFileName = args[1];
        this.workersToFileRation = Integer.parseInt(args[2]);
        if(args.length == 4){
            this.terminate = args[3].equals("terminate");
        }


        List<String> managers = listManagerInstances();
        int numberOfManagers = managers.size();
        if(numberOfManagers == 0){
            //TODO: initiate manager instance
            
        }
        else if(numberOfManagers > 1){
            //TODO: check what to do if more than one manager exists
        }else{
            
        }
    }

    private List<String> listManagerInstances() {
        List<String> instanceIds = new ArrayList<>();
        
        Filter tagFilter = Filter.builder()
                .name("tag:" + MANAGER_TAG_KEY) // Format for tag filtering is always 'tag:<key>'
                .values(MANAGER_TAG_VALUE)
                .build();
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(tagFilter)
                .build();
        DescribeInstancesResponse response = ec2.describeInstances(request);
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                System.out.printf(
                        "Found instance with ID %s, " +
                                "AMI %s, " +
                                "type %s, " +
                                "state %s " +
                                "and monitoring state %s%n",
                        instance.instanceId(),
                        instance.imageId(),
                        instance.instanceType(),
                        instance.state().name(),
                        instance.monitoring().state());//for debugging purposes
                instanceIds.add(instance.instanceId());
                    }
        }
        return instanceIds;
    }

    private void uploadFileToS3(String bucketName, String keyName, String filePath) {
        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("FATAL: Input file not found" + inputFilePath);
        }
        s3.putObject(builder -> builder.bucket(bucketName).key(keyName).build(),
                RequestBody.fromFile(inputFile));
    }
}