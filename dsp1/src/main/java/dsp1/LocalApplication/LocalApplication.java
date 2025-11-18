package dsp1.LocalApplication;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ec2.model.*;

import java.io.File; 

import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;

public class LocalApplication{
    
    private final Ec2Client ec2 = Ec2Client.builder()
            .region(Region.US_EAST_1)
            .build();
    //NOTE: temprary

    private static final String MANAGER_TAG_KEY = "Role";
    private static final String MANAGER_TAG_VALUE = "Manager";

    Filter tagFilter = Filter.builder()
            .name("tag:" + MANAGER_TAG_KEY) // Format for tag filtering is always 'tag:<key>'
            .values(MANAGER_TAG_VALUE)
            .build();
            
    DescribeInstancesResponse response =  ec2.describeInstances().builder()
            .filter(tagFilter).build();


}