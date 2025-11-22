package dsp1.ManagerApplication;

import dsp1.AWS;
import org.json.JSONObject;
import software.amazon.awssdk.services.sqs.model.*;

public class ManagerApplication {

    private static final String LOCAL_TO_MANAGER = "LocalToManagerQueue";
    private static final String MANAGER_TO_LOCAL = "ManagerToLocalQueue";

    private static final AWS AWSinstance = AWS.getInstance();



    public static void createQueue(String queueName) {
        AWSinstance.createSqsQueue(queueName);
        System.out.println("Queue created: " + queueName);
    }

    public static String getQueueUrl(String queueName) {
        return AWSinstance.getSqs().getQueueUrl(
                GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build()
        ).queueUrl();
    }

    public static Message receiveMessage(String queueName) {
        ReceiveMessageRequest req =
                ReceiveMessageRequest.builder()
                        .queueUrl(getQueueUrl(queueName))
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(10)
                        .build();

        var msgs = AWSinstance.getSqs().receiveMessage(req).messages();

        if (msgs.isEmpty())
            return null;

        return msgs.get(0);
    }

    public static void deleteMessage(String queueName, Message msg) {
        AWSinstance.getSqs().deleteMessage(
                DeleteMessageRequest.builder()
                        .queueUrl(getQueueUrl(queueName))
                        .receiptHandle(msg.receiptHandle())
                        .build()
        );
    }

//----------------------------------main-------------------------------------------------------
    public static void main(String[] args) {

        // 1) Create queues
        createQueue(LOCAL_TO_MANAGER);
        createQueue(MANAGER_TO_LOCAL);

        System.out.println("Manager started. Waiting for messages...");

        while (true) {

            // 2) Receive message from LocalApps
            Message msg = receiveMessage(LOCAL_TO_MANAGER);

            if (msg == null)
                continue;

            // 3) Parse JSON from message
            JSONObject obj = new JSONObject(msg.body());

            String type = obj.getString("type");

            if (type.equals("newTask")) {

                String bucket = obj.getString("s3Bucket");
                String key = obj.getString("s3Key");
                String inputFile = obj.getString("inputFile");
                String outputFile = obj.getString("outputFile");
                int workers = obj.getInt("workers");
                boolean terminate = obj.getBoolean("terminate");

                System.out.println("Received new task from LocalApp:");
                System.out.println("- bucket: " + bucket);
                System.out.println("- key: " + key);
                System.out.println("- input: " + inputFile);
                System.out.println("- output: " + outputFile);
                System.out.println("- workers: " + workers);

                // TODO:
                // 1. download input from S3
                // 2. read URLs
                // 3. send messages to workers queue
                // 4. launch workers

            }

            else if (type.equals("terminate")) {
                System.out.println("Terminate signal received.");
                break;
            }

            deleteMessage(LOCAL_TO_MANAGER, msg);
        }
    }


}
