package dsp1.LocalApplication;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalApplication {

/////input 
    private String inputFilePath;
    private String outputFileName;
    private int workersToFileRation;
    private boolean terminate;



    private static final String MANAGER_TAG_KEY = "Role";
    private static final String MANAGER_TAG_VALUE = "Manager";



    // AWS instance
    private final AWS aws;
    private final Ec2Client ec2;

    // constructor
    public LocalApplication() {
        this.aws = AWS.getInstance();
        this.ec2 = Ec2Client.builder()
                                      .region(AWS.region2)  
                                                           .build();
    }
   ///////////////////////////////////////////////// manger handle //////////////////////////////////////////////////
//search in all instances for manger
    private List<Instance> listManagerInstances() {

        List<Instance> instances = new ArrayList<>();
        Filter tagFilter = Filter.builder()
                                        .name("tag:" + MANAGER_TAG_KEY)
                                                           .values(MANAGER_TAG_VALUE)
                                                                                .build();

        DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(tagFilter).build();
        DescribeInstancesResponse response = ec2.describeInstances(request);

        //go about all instances and make list 
           for (Reservation reservation : response.reservations()) {
    for (Instance instance : reservation.instances()) {
            String id = instance.instanceId();
            System.out.println("Found  Manager instance: " + id + " state=" + instance.state().name());
            instances.add(instance);
        
    }
}
             return instances;

        
                   }

private String ensureManagerRunning() {
    List<Instance> managers = listManagerInstances();

    if (managers.isEmpty()) {
        String userDataScript = ""; 

        String newManagerId = aws.createEC2(userDataScript, "Manager", 1);
        System.out.println("No managers found. Started new Manager with id: " + newManagerId);
        return newManagerId;
    }

    Instance manager = managers.get(0);
    String id = manager.instanceId();
    System.out.println("Using existing Manager with id: " + id +
                       " and state=" + manager.state().name());
    return id;
}
//////////////////////////////////////////////////handel input /////////////////////////////////////////////
  private void parseArgs(String[] args) {
    if (args.length < 3) {
        throw new IllegalArgumentException(
                "false input");
    }

    this.inputFilePath = args[0];
    this.outputFileName = args[1];
    this.workersToFileRation = Integer.parseInt(args[2]);
    this.terminate = (args.length >= 4) && args[3].equalsIgnoreCase("terminate");
}

private String uploadInputToS3() {

    aws.createBucketIfNotExists(aws.bucketName);

    String fileName = new File(this.inputFilePath).getName();  
    String keyName = "inputs/" + fileName;
    S3Client s3 = S3Client.builder()
                       .region(AWS.region1)
                                     .build();

    File f = new File(this.inputFilePath);
    if (!f.exists()) {
        throw new IllegalArgumentException("Input file not found: " + this.inputFilePath);
    }

    s3.putObject(
            PutObjectRequest.builder()
                    .bucket(aws.bucketName)
                             .key(keyName)
                                    .build(),
                                           RequestBody.fromFile(f)
    );

    System.out.println("Uploaded " + this.inputFilePath +
            " to s3://" + aws.bucketName + "/" + keyName);

    return keyName;
}

///////////////////////////////////main/////////////////////////////////////////////////////////
    public static void main(String[] args) {
    LocalApplication app = new LocalApplication();
    app.parseArgs(args);
    String managerId = app.ensureManagerRunning();
    String key = app.uploadInputToS3();

}





}
