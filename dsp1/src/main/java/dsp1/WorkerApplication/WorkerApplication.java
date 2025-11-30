package dsp1.WorkerApplication;
import java.io.File;

import dsp1.AWS;

import org.json.JSONObject;

import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import java.util.List;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


public class WorkerApplication {
    private static final AWS AWSinstance = AWS.getInstance();
    public static  String erorrMessage = "";
    private static final String WorkerManagerQueueName = "WorkersToManagerQueue";
    private static String WorkerManagerQueueURL;
    private static final String ManagerWorkerQueueName = "ManagerToWorkersQueue";
    private static String ManagerWorkerQueueURL;
    private static String currentTaskId;

    public static void main(String[] args) {
        ManagerWorkerQueueURL = AWSinstance.getSqs().getQueueUrl(GetQueueUrlRequest.builder()
            .queueName(ManagerWorkerQueueName)
            .build()).queueUrl();

        WorkerManagerQueueURL = AWSinstance.getSqs().getQueueUrl(GetQueueUrlRequest.builder()
            .queueName(WorkerManagerQueueName)
            .build()).queueUrl();

        while(true) { 
            System.out.println("Worker is waiting for tasks...");
            Message msg = receiveMessage(ManagerWorkerQueueURL);
            if (msg != null) {
                System.out.println("Worker received a task message.");
                System.out.println("Message body: " + msg.body());
                JSONObject messageJson = new JSONObject(msg.body());
                System.out.println("Worker received message: " + messageJson.toString());
                currentTaskId = messageJson.getString("taskId");
                String url = messageJson.getString("url");
                String analysis = messageJson.getString("analysis");
                erorrMessage="";
                
                File taskFile = downloadFileFromURL(url, currentTaskId, "./");
                if(!erorrMessage.equals("")){
                    deleteMessage(ManagerWorkerQueueURL, msg);    
                    sendMessageToManager(new JSONObject()
                        .put("taskId", currentTaskId)
                        .put("type", "failedjob")
                        .put("error", erorrMessage)
                        .put(analysis, msg)
                        .toString()
                        );
                    continue;
                }
                File resultFile = analyseFile(taskFile, analysis, currentTaskId); 
                if(!erorrMessage.equals("")){
                    deleteMessage(ManagerWorkerQueueURL, msg);    
                    sendMessageToManager(new JSONObject()
                        .put("taskId", currentTaskId)
                        .put("type", "failedjob")
                        .put("error", erorrMessage)
                        .put(analysis, msg)
                        .toString()
                        );
                    continue;
                }
                else {
                    System.out.println("Analysis complete for task" );   
                    System.out.println("starting upload to s3...");
                    String keyName =  "results/" + currentTaskId + "_output.txt_" + url.replace('/','-')+ "_" + analysis;
                    uploadFileToS3(resultFile, keyName);
                    deleteMessage(ManagerWorkerQueueURL, msg);    
                    sendMessageToManager(new JSONObject()
                    .put("taskId", currentTaskId)
                    .put("type", "jobDone")
                    .put("result", keyName)
                    .put("url", url)
                    .put("analysis", analysis)
                    .toString()
                    );
                }
            }
        }          
    }

    public static void sendMessageToManager(String messageBody) {
        try {
            AWSinstance.getSqs().sendMessage(builder -> builder
                .queueUrl(WorkerManagerQueueURL)
                .messageBody(messageBody)
                );
        }
        catch (Exception e) {
            erorrMessage="Failed to send message to manager: " + e.getMessage();
            System.err.println(erorrMessage);
        }
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

    private static void uploadFileToS3(File file, String keyName) {
        try {
            AWSinstance.getS3().putObject(
            software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                .bucket("dsp-assignment1-12025111913") // NOTE: hardcoded bucket name
                .key(keyName)
                .build(),
                file.toPath()
                );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage());
        }
    }



    private static File analyseFile(File file, String analysisType, String taskId) {
        StanfordNLP nlp = StanfordNLP.getInstance();
        File outputFile = new File("/tmp/" + taskId + "_output.txt");

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            StringBuilder sb = new StringBuilder();

            for (String line : lines) {
                if (line.isBlank())
                    continue;

                String result;

                switch (analysisType) {
                    case "POS":
                        result = nlp.analyzePOS(line);
                        break;
                    case "CONSTITUENCY":
                        result = nlp.analyzeConstituency(line);
                        break;
                    case "DEPENDENCY":
                        result = nlp.analyzeDependency(line);
                        break;
                    default:
                        result = "Unknown analysis type: " + analysisType;
                }

                sb.append("INPUT: ").append(line).append("\n");
                sb.append("OUTPUT: ").append(result).append("\n\n");
            }

            Files.writeString(outputFile.toPath(), sb.toString());
            return outputFile;

        } catch (Exception e) {
            erorrMessage="Failed to analyze file: " ;
            System.err.println(erorrMessage + e.getMessage());
            return null;
        }
    }




    public static void deleteMessage(String queueURL, Message msg) {
        try {
            AWSinstance.getSqs().deleteMessage(
                DeleteMessageRequest.builder()
                    .queueUrl(queueURL)
                    .receiptHandle(msg.receiptHandle())
                    .build()
                );
            System.out.println("Message deleted successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete message: " + e.getMessage());
            }
        }
    

    private static File downloadFileFromURL(String url, String taskId, String destinationPath) {
        try{
            InputStream in = new URL(url).openStream();
            Files.copy(in, Paths.get(destinationPath + "/" + taskId + "_inputfile"), StandardCopyOption.REPLACE_EXISTING);
            return new File(destinationPath + "/" + taskId + "_inputfile");

        } 
        catch (Exception e) {
            erorrMessage="Failed to download file from URL: " + e.getMessage();
            System.err.println(erorrMessage);
            return null;
        }
    }
}