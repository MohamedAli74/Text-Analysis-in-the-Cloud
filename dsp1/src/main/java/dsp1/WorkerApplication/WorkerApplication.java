package dsp1.WorkerApplication;
import java.io.File;

import dsp1.AWS;

import org.json.JSONObject;

import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
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
            String bucketname = messageJson.getString("bucketname");
            File taskFile = downloadFileFromURL(url, currentTaskId, "./");
           
            File resultFile = analyseFile(taskFile, analysis, currentTaskId);    
           
            uploadFileToS3(resultFile, bucketname, "results/" + currentTaskId + "_output.txt");

            deleteMessage(WorkerManagerQueueURL, msg);
            sendMessageToManager(new JSONObject()
                        .put("taskId", currentTaskId)
                        .put("type", "jobDone")
                        .put("result", "results/" + currentTaskId + "_output.txt")
                        .toString()
                );
        }
           

       }
         
       public static void sendMessageToManager(String messageBody) {
        AWSinstance.getSqs().sendMessage(builder -> builder
                .queueUrl(WorkerManagerQueueURL)
                .messageBody(messageBody)
        );

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
    
    private static void uploadFileToS3(File file, String bucketName, String keyName) {
        try {
            AWSinstance.getS3().putObject(
                    software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(keyName)
                            .build(),
                    file.toPath()
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }



    private static File analyseFile(File file, String analysisType, String taskId) {
    StanfordNLP nlp = new StanfordNLP();
    File outputFile = new File("/tmp/" + taskId + "_output.txt");

    try {
        List<String> lines = Files.readAllLines(file.toPath());
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (line.isBlank()) continue;

            String result;
            if (analysisType.equals("POS")) {
                result = nlp.analyzePOS(line);
            } else if (analysisType.equals("Constituency")) {
                result = nlp.analyzeConstituency(line);
            } else if (analysisType.equals("Dependencies")) {
                result = nlp.analyzeDependency(line);
            } else {
                result = "Unknown analysis type: " + analysisType;
            }

            sb.append("INPUT: ").append(line).append("\n");
            sb.append("OUTPUT: ").append(result).append("\n\n");
        }

        Files.writeString(outputFile.toPath(), sb.toString());
        return outputFile;

    } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("Analysis failed", e);
    }
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