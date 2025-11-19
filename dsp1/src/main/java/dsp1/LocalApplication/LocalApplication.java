package dsp1.LocalApplication;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.core.sync.RequestBody; 
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.nio.file.Paths;
import java.io.File;

import java.util.ArrayList;
import java.util.List;


import java.io.File; 
import java.net.URL;

import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class LocalApplication{
    
    //args
    private static File inputFile;
    private static String inputFileName;
    private static String outputFileName;
    private static int workersToFileRation;
    private static boolean terminate;
    //AWS clients
    private static final Region region = Region.US_EAST_1;
    private static final String MANAGER_TAG_KEY = "Role";
    private static final String MANAGER_TAG_VALUE = "Manager";
    public static final String S3_BUCKET_NAME = "dsp-assignment1-2025111913";

    private static final Ec2Client ec2 = Ec2Client.builder()
            .region(region)
            .build();
    
    private static final S3Client s3 = S3Client.builder()
            .region(region)
            .build();
    
    private static final SqsClient LocalManagerSQS = SqsClient.builder()//for sending messages to the manager
            .region(region)
            .build();
    private static final SqsClient ManagerLocalSQS = SqsClient.builder()//for getting messages from the manager
            .region(region)
            .build();


    public void main(String args[]) {
        //parse the arguments
        this.inputFile = getFileFromResources(args[0]);
        this.inputFileName = args[0];
        this.outputFileName = args[1];
        this.workersToFileRation = Integer.parseInt(args[2]);
        if(args.length == 4){
            this.terminate = args[3].equals("terminate");
        }

        //assure that the S3 buckcet exists
        CheckPucketExistence();

        //assure that a manager node exists
        List<String> managers = listManagerInstances();
        int numberOfManagers = managers.size();
        if(numberOfManagers == 0){
            //TODO: initiate manager instance  
        }
        else if(numberOfManagers > 1){
            //TODO: check what to do if more than one manager exists
        }

        //upload the input file to S3
        String s3KeyName = Paths.get(inputFileName).getFileName().toString();
        /* 
        uploadFileToS3(S3_BUCKET_NAME, s3KeyName);
        */
    }

    public static List<String> listManagerInstances() {
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
    public static void CheckPucketExistence() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(S3_BUCKET_NAME).build());
            System.out.println("Bucket already exists: " + S3_BUCKET_NAME);
            
        } catch (NoSuchBucketException e) {
            System.out.println("Bucket does not exist. Creating bucket: " + S3_BUCKET_NAME);
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .build();
            s3.createBucket(createBucketRequest);
            System.out.println("Bucket created: " + S3_BUCKET_NAME);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException("FATAL: S3 bucket " + S3_BUCKET_NAME + " does not exist.");//TODO: change
            } else {
                throw e;
            }
        }
    }

    public static void uploadFileToS3(String bucketName, String keyName, File REMOVE_THIS) {//
        //TODO: check that we are handling the File parsing correctly
        inputFile = REMOVE_THIS;//NOTE!!
        inputFileName = "name";//NOTE!!
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("FATAL: Input file not found" + inputFileName);
        }
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();
        s3.putObject(putObject, RequestBody.fromFile(inputFile));
    }

    public File getFileFromResources(String fileName) {
        URL resource = getClass().getClassLoader().getResource(fileName);

        if (resource == null) {
            throw new IllegalArgumentException("File not found in resources folder: " + fileName);
        }

        try {
            return new File(resource.toURI());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file from resources", e);
        }
    }
}