package dsp1.WorkerApplication;
import java.io.File;

import dsp1.AWS;

import org.json.JSONObject;

import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;


import java.io.StringReader;
import java.util.List;
import java.util.Properties;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


public class WorkerApplication {
    private static final AWS AWSinstance = AWS.getInstance();

    private static final String WorkerManagerQueueName = "WorkerToManagerQueue";
    private static String WorkerManagerQueueURL;

    private static final String ManagerWorkerQueueName = "ManagerToWorkerQueue";
    private static String ManagerWorkerQueueURL;

    private static String currentTaskId;

    public static void main(String[] args) {
        ManagerWorkerQueueURL = AWSinstance.getSqs().getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(ManagerWorkerQueueName)
                .build()).queueUrl();

        WorkerManagerQueueURL = AWSinstance.getSqs().getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(WorkerManagerQueueName)
                .build()).queueUrl();

        while(true){ 
            Message msg = receiveMessage(WorkerManagerQueueURL);
            if (msg == null) {
                continue;
            }
            JSONObject messageJson = new JSONObject(msg.body());
            currentTaskId = messageJson.getString("taskId");
            String url = messageJson.getString("url");
            String analysis = messageJson.getString("analysis");
            File taskFile = downloadFileFromURL(url, currentTaskId, "./");//TODO: change destination path
            analyseFile(taskFile, analysis);
            //TODO: upload results to S3 and send message to Manager
            deleteMessage(WorkerManagerQueueURL, msg);
        }

        //System.out.println("Worker Application finished.");
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
    private static void analyseFile(File file, String analysisType) {
        //TODO: perform analysis    
    }

    public static void deleteMessage(String queueURL, Message msg) {
        AWSinstance.getSqs().deleteMessage(
                DeleteMessageRequest.builder()
                        .queueUrl(queueURL)
                        .receiptHandle(msg.receiptHandle())
                        .build()
        );
    }

    private static File downloadFileFromURL(String url, String taskId, String destinationPath) {//NODE
        try{
            InputStream in = new URL(url).openStream();
            Files.copy(in, Paths.get(destinationPath + "/" + taskId + "_inputfile"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new File(destinationPath + "/" + taskId + "_inputfile");
    }
}