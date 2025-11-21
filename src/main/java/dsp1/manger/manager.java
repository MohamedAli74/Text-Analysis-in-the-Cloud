package dsp1.manager;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.s3.S3Client;


public calss manager {

      private static final String LOCAL_TO_MANAGER_QUEUE = "LocalToManagerQueue";
      private static final String MANAGER_TO_LOCAL_QUEUE = "ManagerToLocalQueue";
      
      private static final SqsClient sqs = SqsClient.builder().region(AWS.region1).build();
       private static final S3Client s3 = S3Client.builder().region(AWS.region1).build();



  // public string receiveMessageFromLocalApp() {  }






 pucblic static void main ( String args[])
{
 














}













}