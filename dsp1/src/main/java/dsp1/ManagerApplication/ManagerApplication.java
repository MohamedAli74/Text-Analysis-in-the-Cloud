package dsp1.ManagerApplication;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import dsp1.AWS;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

class ManagerApplication{
    private static final String ManagerLocalQueueName = "ManagerToLocalQueue";
    private static String ManagerLocalQueueURL;
    
    private static final String LocalManagerQueueName = "LocalToManagerQueue";
    private static String LocalManagerQueueURL;
    
    private static final AWS AWSinstance = AWS.getInstance();

    private static final String WorkerTagKey = "Role";
    private static final String WorkerTagValue = "Worker";
    
    public static void main(String[] args) {
        createlQueue(ManagerLocalQueueName);
        createlQueue(LocalManagerQueueName);
    }

    public static void createlQueue(String queueName) {
        AWSinstance.createSqsQueue(queueName);
        System.out.println(" Queue " + queueName + " created.");
    }

    public static String getQueueUrl(String queueName) {
    return AWSinstance.getSqs().getQueueUrl(
        GetQueueUrlRequest.builder()
        .queueName(queueName)
        .build()
    ).queueUrl();
}
}