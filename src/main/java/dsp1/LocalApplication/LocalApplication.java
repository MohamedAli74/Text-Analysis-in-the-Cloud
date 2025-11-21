package dsp1.LocalApplication;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.core.sync.RequestBody;

import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.model.*;

import java.nio.file.Paths;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LocalApplication {

    // args
    private static File inputFile;
    private static String inputFileName;
    private static String outputFileName;
    private static int workersToFileRation;
    private static boolean terminate;

    // AWS config
    private static final Region region = Region.US_EAST_1;
    public static final String MANAGER_TAG_KEY = "Role";
    public static final String MANAGER_TAG_VALUE = "Manager";
    public static final String S3_BUCKET_NAME = "dsp-assignment1-2025111913";

    private static final Ec2Client ec2 = Ec2Client.builder().region(region).build();
    private static final S3Client s3 = S3Client.builder().region(region).build();

    //-------------------------------------------------------- LIST MANAGER and check if it is exist--------------------------------------------------------
    public static List<String> listManagerInstances() {
        List<String> instanceIds = new ArrayList<>();

        Filter tagFilter = Filter.builder()
                .name("tag:" + MANAGER_TAG_KEY)
                .values(MANAGER_TAG_VALUE)
                .build();

        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(tagFilter)
                .build();

        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation res : response.reservations()) {
            for (Instance instance : res.instances()) {

                String state = instance.state().nameAsString();
                if (state.equals("terminated") || state.equals("stopped") || state.equals("shutting-down")) continue;

                System.out.printf("Found Manager: %s (%s)%n",
                        instance.instanceId(), state);

                instanceIds.add(instance.instanceId());
            }
        }

        return instanceIds;
    }


    public static String getOrCreateManagerInstance() {
        AWS aws = AWS.getInstance();
        List<String> managers = listManagerInstances();

        if (!managers.isEmpty()) {
            String existing = managers.get(0);
            System.out.println(" Manager instance already running: " + existing);
            return existing;
        }

        System.out.println(" No manager found. Launching new one...");

        String script = ""; // add startup script
        String newId = aws.createEC2(script, "Manager", 1);

        System.out.println(" Launched Manager with ID: " + newId);
        return newId;
    }


    //-------------------------------------------------------- uploud input to s3 -----------------------------------------------------------
    public static void CheckPucketExistence() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(S3_BUCKET_NAME).build());
            System.out.println("Bucket already exists: " + S3_BUCKET_NAME);

        } catch (NoSuchBucketException e) {
            System.out.println("Bucket not found. Creating...");
            s3.createBucket(CreateBucketRequest.builder().bucket(S3_BUCKET_NAME).build());
            System.out.println("Bucket created.");
        }
    }


    public static void uploadFileToS3(String bucketName, String keyName, File file) {
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(bucketName).key(keyName).build();

        s3.putObject(putObject, RequestBody.fromFile(file));
        System.out.println(" File uploaded: " + keyName);
    }


    public static File getFileFromResources(String fileName) {
        URL resource = LocalApplication.class.getClassLoader().getResource(fileName);

        if (resource == null)
            throw new IllegalArgumentException("File NOT FOUND in resources: " + fileName);

        try {
            return new File(resource.toURI());
        } catch (Exception e) {
            throw new RuntimeException("Error loading resource file", e);
        }
    }


    //-------------------------------------------------------- SQS  message ---------------------------------------------------------------
    public static void createLocalToManagerQueue() {
        AWS.getInstance().createSqsQueue("LocalToManagerQueue");
        System.out.println(" Queue LocalToManagerQueue created.");
    }


    public static String getLocalToManagerQueueUrl() {
        return AWS.getInstance().getSqs().getQueueUrl(
                GetQueueUrlRequest.builder()
                        .queueName("LocalToManagerQueue")
                        .build()
        ).queueUrl();
    }


    public static void sendS3PathToManager(String bucketName, String keyName) {

        String queueUrl = getLocalToManagerQueueUrl();
        String s3Path = "s3://" + bucketName + "/" + keyName;

        SendMessageRequest sendMsg = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(s3Path)
                .build();

        AWS.getInstance().getSqs().sendMessage(sendMsg);

        System.out.println("Sent message to Manager: " + s3Path);
    }


    //-------------------------------------------------------- MAIN ---------------------------------------------------------------
    public static void main(String[] args) {

        if (args.length < 3) {
            System.err.println(" LocalApplication <inputFile> <outputFile> <workers> [terminate]");
            return;
        }

        inputFileName = args[0];
        outputFileName = args[1];
        workersToFileRation = Integer.parseInt(args[2]);
        terminate = (args.length == 4 && args[3].equals("terminate"));

        inputFile = getFileFromResources(inputFileName);
        CheckPucketExistence();

        String managerId = getOrCreateManagerInstance();

        String key = Paths.get(inputFileName).getFileName().toString();
        uploadFileToS3(S3_BUCKET_NAME, key, inputFile);

        createLocalToManagerQueue();
        sendS3PathToManager(S3_BUCKET_NAME, key);

    }
}
