package dsp1.ManagerApplication;

import dsp1.AWS;

import java.io.File;
import software.amazon.awssdk.services.ec2.model.Filter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

import org.json.JSONObject;

import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.model.*;

public class ManagerApplication {
        
    private static final String LOCAL_TO_MANAGER = "LocalToManagerQueue";
    private static String LocalManagerQueueURL;
    private static final String MANAGER_TO_LOCAL = "ManagerToLocalQueue";
    private static String ManagerLocalQueueURL;
    
    private static final String WORKERS_TO_MANAGER = "LocalToManagerQueue";
    private static String WorkersManagerQueueURL;
    private static final String MANAGER_TO_WORKERS = "ManagerToLocalQueue";
    private static String ManagerWorkersQueueURL;

    private static final AWS AWSinstance = AWS.getInstance();


    // -------------------------------------------------------------
    // Create Queue
    // -------------------------------------------------------------
    public static void createQueue(String queueName) {
        AWSinstance.getSqs().createQueue(
                CreateQueueRequest.builder()
                        .queueName(queueName)
                        .build()
        );
        System.out.println("Queue created: " + queueName);
    }

    public static String getQueueUrl(String queueName) {
        return AWSinstance.getSqs().getQueueUrl(
                GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build()
        ).queueUrl();
    }

    public static Message receiveMessage(String queueURL) {
        ReceiveMessageRequest req =
                ReceiveMessageRequest.builder()
                        .queueUrl(queueURL)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(10)
                        .build();

        var msgs = AWSinstance.getSqs().receiveMessage(req).messages();

        if (msgs.isEmpty())
            return null;

        return msgs.get(0);
    }

    //-------------------------------------- Send message to Local
    // -------------------------------------------------------------
    public static void sendMessageToLocal(String msgBody) {
        AWSinstance.getSqs().sendMessage(
                SendMessageRequest.builder()
                        .queueUrl(getQueueUrl(MANAGER_TO_LOCAL))
                        .messageBody(msgBody)
                        .build()
        );
    }

    // Delete SQS Message
    // -------------------------------------------------------------
    public static void deleteMessage(String queueName, Message msg) {
        AWSinstance.getSqs().deleteMessage(
                DeleteMessageRequest.builder()
                        .queueUrl(getQueueUrl(queueName))
                        .receiptHandle(msg.receiptHandle())
                        .build()
        );
    }

    // ---------------------------------------Download input file from S3
    public static String downloadFileFromS3(String filename) {

        String localName = "downloaded_" + filename;

        GetObjectRequest obj = GetObjectRequest.builder()
                .bucket("dsp-assignment1-2025111913")//ممكن نغير لقدام 
                .key(filename)
                .build();

        AWSinstance.getS3().getObject(obj, Paths.get(localName));

        System.out.println("Downloaded file from S3: " + filename);

        return localName;
    }

    // --------------------------------------------Parse Input File → return List<JSON>
    public static List<JSONObject> parseInputFileAsJson(String fileName, String taskId) {

        List<JSONObject> jsonList = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(Paths.get(fileName));

            for (String line : lines) {

                if (line.trim().isEmpty())
                    continue;

                String[] parts = line.split("\\t");

                if (parts.length < 2) {
                    System.out.println("Skipping bad line: " + line);
                    continue;
                }

                String analysis = parts[0].trim();
                String url = parts[1].trim();

                JSONObject obj = new JSONObject();
                obj.put("type", "workerTask");
                obj.put("taskId", taskId);
                obj.put("analysis", analysis);
                obj.put("url", url);

                jsonList.add(obj);
            }
        
        } catch (Exception e) {
            System.out.println("Error reading input file: " + e.getMessage());
        }
    
        return jsonList;
    }

//-------------------------------------------send tasks to workers

    public static void sendTaskToWorkers(JSONObject taskJson) {

    String queueUrl = getQueueUrl("ManagerToWorkerQueue");

    SendMessageRequest sendMsg = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(taskJson.toString())
            .build(); 

    AWSinstance.getSqs().sendMessage(sendMsg);

    System.out.println("Sent task to workers: " + taskJson.toString());
}
//--------------------------------------------get running worker instances
public static int getRunningWorkersCount() {

    Filter filter = Filter.builder()
            .name("tag:Role")
            .values("Worker")
            .build();

    DescribeInstancesResponse res =
            AWSinstance.getEc2().describeInstances(
                    DescribeInstancesRequest.builder()
                            .filters(filter)
                            .build()
            );

    int count = 0;

    for (Reservation r : res.reservations()) {
        for (Instance i : r.instances()) {

            if (i.state().nameAsString().equals("running")) {
                count++;
            }
        }
    }

    return count;
}

//--------------------------------------------create worker instances
  public static void createWorkerInstances(int numberOfWorkers) {
    String workerScript = "";//chnnge
    AWSinstance.createEC2(workerScript, "Worker", numberOfWorkers);
    System.out.println("Created " + numberOfWorkers + " worker instances.");
  }


  //--------------------------------------- parse and distribute tasks to workers
   public static int AvailableWorker() {
    int maxWorkers = 17;
    int runningWorkers = getRunningWorkersCount();
    return maxWorkers - runningWorkers;

}

  
  public static void givetaskstoWorkers(List<JSONObject> tasks, int tasksPerWorker) {

    int runningWorkers = getRunningWorkersCount();
    int requiredWorkers = (int) Math.ceil((double) tasks.size() / tasksPerWorker);

    System.out.println("Workers needed = " + requiredWorkers);
    System.out.println("Workers running = " + runningWorkers);

    int toLaunch = requiredWorkers - runningWorkers;

    if (toLaunch > 0) {

        int place = AvailableWorker(); 
        if (place == 0) {
            System.out.println(" Maximum 18 workers reached. Cannot launch more.");
        } else {

            int finalLaunchNumber = Math.min(toLaunch, place);

            System.out.println("Launching " + finalLaunchNumber + " workers (max allowed).");

            createWorkerInstances(finalLaunchNumber);
        }
    }
    for (JSONObject task : tasks) {
        sendTaskToWorkers(task);
    }

    System.out.println("Distributed " + tasks.size() + " tasks to workers.");
}

    // --------------------------------------------MAIN
    
    public static void main(String[] args) {

        createQueue(LOCAL_TO_MANAGER);
        LocalManagerQueueURL = getQueueUrl(LOCAL_TO_MANAGER);
        createQueue(MANAGER_TO_LOCAL);
        ManagerLocalQueueURL = getQueueUrl(MANAGER_TO_LOCAL);
        
        createQueue(WORKERS_TO_MANAGER);
        WorkersManagerQueueURL = getQueueUrl(WORKERS_TO_MANAGER);
        createQueue(MANAGER_TO_WORKERS);
        ManagerWorkersQueueURL = getQueueUrl(MANAGER_TO_WORKERS);

        System.out.println("Manager started. Waiting for messages...");

        while (true) {

            // 2) Receive message from LocalApps
            Message msg = receiveMessage(LocalManagerQueueURL);

            if (msg == null)
                continue;

            JSONObject obj = new JSONObject(msg.body());
            String type = obj.getString("type");

            if (type.equals("newTask")) {

                String bucket = obj.getString("s3Bucket");
                String taskid = obj.getString("taskId");
                String key = obj.getString("inputFile");
                int workersToFileRation = obj.getInt("workers");

                System.out.println("Received new task:");
                System.out.println("- task id: " + taskid);
                System.out.println("- bucket:  " + bucket);
                System.out.println("- file key: " + key);

                // 1. Download file
                String localFile = downloadFileFromS3(key);

                // 2. Parse to JSON list
                List<JSONObject> tasks = parseInputFileAsJson(localFile, taskid);

                System.out.println("Parsed " + tasks.size() + " tasks from input file.");

                // TODO: send tasks to workers, create workers, handle responses...

            }

            else if (type.equals("terminate")) {
                System.out.println("Terminate signal received.");
                break;
            }

            deleteMessage(LOCAL_TO_MANAGER, msg);
        }
    }
}
