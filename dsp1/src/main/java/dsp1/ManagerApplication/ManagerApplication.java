package dsp1.ManagerApplication;

import dsp1.AWS;

import java.net.URLEncoder;

import software.amazon.awssdk.services.ec2.model.Filter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONObject;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.model.*;

public class ManagerApplication {

    private static final int MANAGER_THREAD_POOL_SIZE = 5; 
    private static ExecutorService requestThreadPool;

    private static ConcurrentHashMap<String, List<String[]>> jobIdToResults = new ConcurrentHashMap<>();//[Analyzation type, Initial URL, Analyzed URL]
    private static ConcurrentHashMap<String , Integer> jobIdToExpectedTasks = new ConcurrentHashMap<>();

    private static final String LOCAL_TO_MANAGER = "LocalToManagerQueue";
    private static String LocalManagerQueueURL;
    private static final String MANAGER_TO_LOCAL = "ManagerToLocalQueue";
    private static String ManagerLocalQueueURL;

    private static final String WORKERS_TO_MANAGER = "WorkersToManagerQueue";
    private static String WorkersManagerQueueURL;
    private static final String MANAGER_TO_WORKERS = "ManagerToWorkersQueue";
    private static String ManagerWorkersQueueURL;

    private static final AWS AWSinstance = AWS.getInstance();
    private static boolean toTerminate = false;

    private static AtomicInteger jobsReceived = new AtomicInteger(0);
    private static AtomicInteger jobsCompleted = new AtomicInteger(0);


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

    //-------------------------------------- Send message to worker --------------------------------

    public static void sendMessageToWorker(String msgBody) {
        AWSinstance.getSqs().sendMessage(
            SendMessageRequest.builder()
                .queueUrl(ManagerWorkersQueueURL)
                .messageBody(msgBody)
                .build()
                );
    }




    // --------------------------msg to local  -----------------------------------
    public static void sendMessageToLocal(String msgBody) {
        AWSinstance.getSqs().sendMessage(
            SendMessageRequest.builder()
                .queueUrl(ManagerLocalQueueURL)
                .messageBody(msgBody)
                .build()
                );
    }

    // Delete SQS Message
    // -------------------------------------------------------------
    public static void deleteMessage(String queueURL, Message msg) {
        AWSinstance.getSqs().deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(queueURL)
                .receiptHandle(msg.receiptHandle())
                .build()
                );
    }

    // ---------------------------------------Download input file from S3
    public static String downloadFileFromS3(String filename) {
        String localName = "downloaded_" + filename.replace("/", "_"); 

        try {
            Files.deleteIfExists(Paths.get(localName)); 
            // -----------------------------------------------------

            GetObjectRequest obj = GetObjectRequest.builder()
                .bucket(AWSinstance.bucketName)
                .key(filename)
                .build();

            AWSinstance.getS3().getObject(obj, Paths.get(localName));
            System.out.println("Downloaded file from S3: " + filename);
            return localName;

        } catch (Exception e) {
            System.out.println("ERROR downloading from S3. key = " + filename);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    public static void uploadFileToS3(String bucketName, String keyName, String htmlFile) {
        PutObjectRequest putObject = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(keyName)
            .contentType("text/html")
            .build();
        AWSinstance.getS3().putObject(putObject, RequestBody.fromString(htmlFile));
    }

    // --------------------------------------------Parse Input File → return List<JSON>
    public static List<JSONObject> parseInputFileAsJson(String fileName, String taskId) {

        List<JSONObject> jsonList = new ArrayList<>();

        try {
            //List<String> lines = Files.readAllLines(Paths.get(fileName));
            String[] lines = Files.readString(Paths.get(fileName)).split("\\r?\\n|\\r");

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

        sendMessageToWorker(taskJson.toString());

        System.out.println("Sent task to workers: " + taskJson.toString());
    }
    //--------------------------------------------get running worker instances
    public static int getRunningWorkersCount() {

        Filter filter = Filter.builder()
        .name("tag:Role")
        .values("Worker")
        .build();

        DescribeInstancesResponse res =AWSinstance.getEc2().describeInstances(
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

    public static List<String> getInstancesOfTag(String tagValue) {
        List<String> instanceIds = new ArrayList<>();
    
        Filter filter = Filter.builder()
        .name("tag:Role")
        .values(tagValue)
        .build();
    
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
        .filters(filter)
        .build();
    
        DescribeInstancesResponse response = AWSinstance.getEc2().describeInstances(request);
    
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                instanceIds.add(instance.instanceId());
            }
        }
    
        return instanceIds;
    }


    //--------------------------------------------create worker instances
    public static void createWorkerInstances(int numberOfWorkers) {
        String workerScript = "#!/bin/bash\n"
                            // 1. Install Java
                            + "yum update -y\n"
                            + "yum install java-17-amazon-corretto -y\n"
                            + "mkdir -p /home/ec2-user/app\n"
                            + "aws s3 cp s3://" + AWSinstance.bucketName + "/system.jar /home/ec2-user/app/system.jar\n"
                            + "nohup java -cp /home/ec2-user/app/system.jar dsp1.WorkerApplication.WorkerApplication > /home/ec2-user/app/worker.log 2>&1 &";
        for(int i = 0 ; i < numberOfWorkers; i++)
            AWSinstance.createEC2(workerScript, "Worker", 1);
        System.out.println("Created " + numberOfWorkers + " worker instances.");
    }


    //--------------------------------------- parse and distribute tasks to workers
    public static int AvailableWorker() {
        int maxWorkers = 7;
        int runningWorkers = getRunningWorkersCount();
        return maxWorkers - runningWorkers;
    }


    public static void startWorkersIfNeeded(List<JSONObject> tasks, int tasksPerWorker) {

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
    }
    private static void sendTasksToWorkers(List<JSONObject> tasks){
        for(JSONObject task : tasks) {
            sendTaskToWorkers(task);
        }
        System.out.println("Distributed " + tasks.size() + " tasks to workers.");
    }   


    ////////////////////////////////////////////handle worker messages////////////////////////////////////////////
    private static void handleNewTaskMessage(Message msg) {
        JSONObject obj = new JSONObject(msg.body());
        String bucket = obj.getString("s3Bucket");
        String taskid = obj.getString("taskId");
        if(toTerminate){
            JSONObject failObject = new JSONObject();
            failObject.put("type", "blocked");
            failObject.put("description", "Unable to recieve task %s\nManager will be terminating soon...".formatted(taskid));
            failObject.put("taskId",taskid);
            sendMessageToLocal(failObject.toString());
            deleteMessage(LocalManagerQueueURL, msg);
            return;
        }
        String key = obj.getString("key");
        int workersToFileRatio = obj.getInt("workers");
        String terminate = obj.optString("terminate");
        if(terminate.equals("true")){
            toTerminate = true;
        }
        
        System.out.println("Received new task:");
        System.out.println("- task id: " + taskid);
        System.out.println("- bucket:  " + bucket);
        System.out.println("- file key: " + key);


        // 0. add to task status map
        jobIdToResults.put(taskid, Collections.synchronizedList(new ArrayList<>()));
        System.out.println("Downloaded input file1  ");

        // 1. Download file
        String localFile = downloadFileFromS3(key);
        System.out.println("Downloaded input file  ");

        // 2. Parse to JSON list
        List<JSONObject> tasks = parseInputFileAsJson(localFile, taskid);
        jobIdToExpectedTasks.put(taskid, tasks.size());

        System.out.println("Parsed " + tasks.size() + " tasks from input file.");

        // 3. workers initiation
        startWorkersIfNeeded(tasks, workersToFileRatio);

        // 4. Distribute tasks to workers
        sendTasksToWorkers(tasks);
        jobsReceived.incrementAndGet();
    }



    private static void handleTerminateMessage(Message msg) {
        System.out.println("Terminate signal received.");
        System.exit(0);
    }

        private static void handleDoneMessage(Message msg) {
            JSONObject obj = new JSONObject(msg.body());
            String taskId = obj.getString("taskId");
            String resultLocation = obj.getString("result");
            String url = obj.getString("url");
            String analysis = obj.getString("analysis");
            String[] subTask = {analysis, url, resultLocation};
            jobIdToResults.get(taskId).add(subTask);
            if (jobIdToResults.get(taskId).size() == jobIdToExpectedTasks.get(taskId)) {
                System.out.println("All tasks for job " + taskId + " are done. Creating summary...");
                List<String[]> results = jobIdToResults.get(taskId);
                String summaryKey = "results/" + taskId + "_summary.html";
                makeSummaryFile(taskId, results, summaryKey);
                System.out.println("Summary file created ");
                JSONObject resultMsg = new JSONObject();
                resultMsg.put("type", "jobDone");
                resultMsg.put("taskId", taskId);
                resultMsg.put("s3Bucket", AWSinstance.bucketName);     
                resultMsg.put("outputS3Key", summaryKey);            

                sendMessageToLocal(resultMsg.toString());
                System.out.println("Sent jobDone to LocalApp.");

                jobIdToResults.remove(taskId);
                jobIdToExpectedTasks.remove(taskId);
                jobsCompleted.incrementAndGet();
            }

    } 

    private static void handlefailedjobMessage(Message msg) {
        JSONObject obj = new JSONObject(msg.body());
        String taskId = obj.getString("taskId");
        String error = obj.getString("error");
        String url = obj.getString("url");
        String analysis = obj.getString("analysis");

        System.out.println("Worker reported failed job for task " + taskId
            + " | analysis=" + analysis
            + " | url=" + url
            + " | error=" + error);

        String[] subTask = { analysis, url, "ERROR: " + error };
        jobIdToResults.get(taskId).add(subTask);

        if (jobIdToResults.get(taskId).size() == jobIdToExpectedTasks.get(taskId)) {
            System.out.println("All tasks for job " + taskId + " are done. Creating summary...");
            List<String[]> results = jobIdToResults.get(taskId);
            String summaryKey = "results/" + taskId + "_summary.html";
            makeSummaryFile(taskId, results, summaryKey);
            System.out.println("Summary file created ");
            JSONObject resultMsg = new JSONObject();
            resultMsg.put("type", "jobDone");
            resultMsg.put("taskId", taskId);
            resultMsg.put("s3Bucket", AWSinstance.bucketName);     
            resultMsg.put("outputS3Key", summaryKey);            

            sendMessageToLocal(resultMsg.toString());
            System.out.println("Sent jobDone to LocalApp.");

            jobIdToResults.remove(taskId);
            jobIdToExpectedTasks.remove(taskId);
            jobsCompleted.incrementAndGet();
        }
}

   
    public static void makeSummaryFile(String taskId, List<String[]> resultKeys, String summaryKey) {
        StringBuilder html = new StringBuilder();

        html.append("<html><head><title>Results for ")
            .append(taskId)
            .append("</title></head><body>");

        html.append("<h1>Analysis Results</h1>");

        for (String[] result : resultKeys) {
            String analysisType = result[0];
            String inputUrl     = result[1];
            String outputField  = result[2];  

            html.append("<p>");

            html.append(analysisType).append(": ");

            html.append("<a href='")
                .append(inputUrl)
                .append("'>")
                .append(inputUrl)
                .append("</a> ");

            // 3) output:
            if (outputField != null && outputField.startsWith("ERROR:")) {
                html.append(outputField);   
            } else {
                try {
                    String encodedKey = URLEncoder.encode(
                            outputField,
                            StandardCharsets.UTF_8.toString()
                    );

                    String publicLink = "https://" + AWSinstance.bucketName
                            + ".s3.us-east-1.amazonaws.com/" + encodedKey;

                    html.append("<a href='")
                        .append(publicLink)
                        .append("'>")
                        .append(publicLink)
                        .append("</a>");

                } catch (Exception e) {
                    html.append("ERROR: ").append(e.getMessage());
                }
            }

            html.append("</p>\n");
        }

        html.append("</body></html>");

        uploadFileToS3(AWSinstance.bucketName, summaryKey, html.toString());
        System.out.println("Summary file uploaded to: " + summaryKey);
    }


   //------------------------------Terminate System---------------------------------
    private static void terminate(){
        System.out.println("Terminating system...");
        List<String> workersInstancesIds = getInstancesOfTag("Worker");
        for(String id : workersInstancesIds){
            terminateEC2(id);
            System.out.println("Terminated instance: "+ id);
        } 
        System.out.println("Manager terminated the system.");
        System.out.println("Exiting...");
        terminateEC2(getInstancesOfTag("Manager").get(0));
        System.exit(0);
    }

    private static void terminateEC2(String instanceId){
        try{
        TerminateInstancesRequest request = TerminateInstancesRequest.builder()
            .instanceIds(instanceId)
            .build();
        AWSinstance.getEc2().terminateInstances(request);
        } catch (Exception e){
            System.out.printf("ERROR terminating instance %s: %s%n ", instanceId, e.getMessage());
        }
    }

   

    // ----------------------------Main---------------------------------    

    public static void main(String[] args) {

        requestThreadPool = Executors.newFixedThreadPool(MANAGER_THREAD_POOL_SIZE);

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
            Message msgFromLocal = receiveMessage(LocalManagerQueueURL);
            if (msgFromLocal != null){
               requestThreadPool.submit(() ->handleIncomingLocal(msgFromLocal));
            }
            
            //----------------------------------------------------------
            
            Message msgFromWorker = receiveMessage(WorkersManagerQueueURL);
            if (msgFromWorker != null){
                requestThreadPool.submit(() ->handleIncomingWorker(msgFromWorker));
            }

            if (jobsReceived.get() == jobsCompleted.get() && jobsReceived.get() > 0) {
                System.out.println("All jobs completed.");
                if(toTerminate){
                    terminate();
                }
                else{
                    jobsReceived = new AtomicInteger(0);
                    jobsCompleted = new AtomicInteger(0);
                }
            }
        }
    }

    private static void handleIncomingLocal(Message msgFromLocal){
         try {
                    JSONObject obj = new JSONObject(msgFromLocal.body());
                    String type = obj.getString("type");
                    System.out.println(msgFromLocal.body());

                    if (type.equals("newTask")) {
                        handleNewTaskMessage(msgFromLocal);

                    } else if (type.equals("terminate")) {//do we need this??
                        handleTerminateMessage(msgFromLocal);
                    } 

                    deleteMessage(LocalManagerQueueURL, msgFromLocal);

                } catch (Exception e) {
                    // This catches SQS API errors, JSON parsing errors, and logic errors.
                    System.err.println("ERROR: Failed to process or delete message: " + e.getMessage());
                }          
    }

    private static void handleIncomingWorker(Message msgFromWorker){
        System.out.println("received message from worker: " + msgFromWorker.body());
        JSONObject obj = new JSONObject(msgFromWorker.body());
        String type = obj.getString("type");
        if (type.equals("jobDone")) {
            deleteMessage(WORKERS_TO_MANAGER, msgFromWorker);
            handleDoneMessage(msgFromWorker);
        }

            if (type.equals("failedjob")) {
            deleteMessage(WORKERS_TO_MANAGER, msgFromWorker);
            handlefailedjobMessage(msgFromWorker);
        }
        
    }
}