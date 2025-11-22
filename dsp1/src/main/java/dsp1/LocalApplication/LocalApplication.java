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
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import dsp1.AWS;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Paths;
import java.io.File;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;


import java.io.File; 
import java.net.URL;

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
    private static final AWS AWSinstance = AWS.getInstance();
    private static final Region region = Region.US_EAST_1;
    private static final String MANAGER_TAG_KEY = "Role";
    private static final String MANAGER_TAG_VALUE = "Manager";
    public static final String S3_BUCKET_NAME = "dsp-assignment1-2025111913";
    private static final String mytaskid=UUID.randomUUID().toString();


    private static final Ec2Client ec2 = Ec2Client.builder()
            .region(region)
            .build();
    
    private static final S3Client s3 = S3Client.builder()
            .region(region)
            .build();

    private static final String ManagerLocalQueueName = "ManagerToLocalQueue"; // Must match Local App's definition
    private static String ManagerLocalQueueURL;
    
    private static final String LocalManagerQueueName = "LocalToManagerQueue"; // Must match Local App's definition
    private static String LocalManagerQueueURL;
    
/////////////////////////////////////////////SQS///////////////////////////////////////


public static String getQueueUrl(String queueName) {
    return AWSinstance.getSqs().getQueueUrl(
        GetQueueUrlRequest.builder()
        .queueName(queueName)
        .build()
    ).queueUrl();
}

 public static void sendJobToManager(String bucketName, String keyName) {

    String messageBody =
        "{"
            + "\"type\":\"newTask\","
            + "\"taskId\":\"" + mytaskid + "\","
            + "\"s3Bucket\":\"" + bucketName + "\","
            + "\"s3Key\":\"" + keyName + "\","
            + "\"inputFile\":\"" + inputFileName + "\","
            + "\"outputFile\":\"" + outputFileName + "\","
            + "\"workers\":" + workersToFileRation + ","
            + "\"terminate\":" + terminate
        + "}";
    
    SendMessageRequest sendMsg = SendMessageRequest.builder()
            .queueUrl(LocalManagerQueueURL)
            .messageBody(messageBody)
            .build();

    AWSinstance.getSqs().sendMessage(sendMsg);

    System.out.println("Sent message to Manager: " + messageBody);
}


/////////////////////////////////////////AWS EC2 Methods//////////////////////////////////////
public static String getOrCreateManagerInstance() {
    List<String> managers = listManagerInstances();
    
    
    if (!managers.isEmpty()) {
        String existing = managers.get(0);
        System.out.println(" Manager instance already running: " + existing);
        return existing;
    }
    
    System.out.println(" No manager found. Launching new one...");
    
    uploadManagerJar(S3_BUCKET_NAME);
    String userDataScript = """
                         #!/bin/bash\n
                         sudo yum update -y\n
                         sudo yum install -y java-17-amazon-corretto\n
                         mkdir -p /home/ec2-user/app\n
                         aws s3 cp s3://" + s3BucketName + "/manager.jar /home/ec2-user/app/manager.jar\n
                         nohup java -jar /home/ec2-user/app/manager.jar > /home/ec2-user/app/log.txt 2>&1 &\n
                         """;

String script = Base64.getEncoder().encodeToString(userDataScript.getBytes());
    String newId = AWSinstance.createEC2(script, "Manager", 1);
    
    System.out.println(" Launched Manager with ID: " + newId);
    return newId;
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
    
    
    //////////////////////////////////////////AWS S3 Methods//////////////////////////////////////
    
    public static void uploadManagerJar(String bucketName) {
        System.out.println("Uploading Manager JAR to S3...");

        String localJarPath = "target/dsp1-1.0-SNAPSHOT.jar"; 
        File jarFile = new File(localJarPath);

        // 2. Verify the JAR exists
        if (!jarFile.exists()) {
            System.err.println("ERROR: JAR file not found at: " + jarFile.getAbsolutePath());
            System.err.println("run 'mvn clean package' first");
            System.exit(1); // Stop execution because Manager cannot run without code
        }

        String s3Key = "manager.jar";

        // 4. Upload
        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3.putObject(putOb, RequestBody.fromFile(jarFile));
            
            System.out.println("✅ Manager JAR uploaded successfully to: s3://" + bucketName + "/" + s3Key);
            
        } catch (Exception e) {
            System.err.println("Failed to upload JAR: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static File getFileFromResources(String fileName) {
        URL resource = LocalApplication.class.getClassLoader().getResource(fileName);
        
        if (resource == null) {
            throw new IllegalArgumentException("File not found in resources folder: " + fileName);
        }
        
        try {
            return new File(resource.toURI());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file from resources", e);
        }
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

    public static void uploadFileToS3(String bucketName, String keyName) {
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("FATAL: Input file not found" + inputFileName);
        }
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();
        s3.putObject(putObject, RequestBody.fromFile(inputFile));
    }

//-----------------------for responses from manager------------------------------
public static Message receiveMessage(String queueUrl) {
    ReceiveMessageRequest request = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(1)
            .waitTimeSeconds(2)
            .build();
    ReceiveMessageResponse response = AWSinstance.getSqs().receiveMessage(request);
    List<Message> messages = response.messages();
    if (messages != null && !messages.isEmpty()) {
        return messages.get(0);
    }
    return null;
}



/////////////////////////////////////////MAIN//////////////////////////////////////////////
        public static void main(String args[]) {

        if (args.length < 3) {
            System.err.println(" LocalApplication <inputFile> <outputFile> <workers> [terminate]");
            return;
        }

        //parse the arguments
        inputFile = getFileFromResources(args[0]);
        inputFileName = args[0];
        outputFileName = args[1];
        workersToFileRation = Integer.parseInt(args[2]);
        if(args.length == 4){
            terminate = args[3].equals("terminate");
        }

        //assure that the S3 buckcet exists
        CheckPucketExistence();

        //assure that a manager node exists
        
        String managerId = getOrCreateManagerInstance();


        ManagerLocalQueueURL = getQueueUrl(ManagerLocalQueueName);
        LocalManagerQueueURL = getQueueUrl(LocalManagerQueueName);
        
        //upload the input file to S3
        String s3KeyName = Paths.get(inputFileName).getFileName().toString();
        uploadFileToS3(S3_BUCKET_NAME, s3KeyName);
        sendJobToManager(S3_BUCKET_NAME, s3KeyName);


        while (true) {
            Message msg = receiveMessage(ManagerLocalQueueURL);
            
            if (msg != null) {
                String body = msg.body();
                System.out.println("Received message from Manager: " + body);
            if (body.contains("\"taskId\":\"" + mytaskid + "\"")) {
                System.out.println(" Task " + mytaskid + " completed.");
            }
                // TODO :  process the message (e.g., download output file from S3)******************
            } else {
                try { Thread.sleep(2000); } catch (Exception e) {}
            }
        } 
    }
}