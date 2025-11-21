package dsp1.LocalApplication;

import java.io.File;

public class LocalTesting {

    public static void main(String[] args) {

        LocalApplication.CheckPucketExistence();

        File f = LocalApplication.getFileFromResources("input.txt");

        LocalApplication.uploadFileToS3(
                LocalApplication.S3_BUCKET_NAME,
                "input.txt",
                f
        );
        String managerId = LocalApplication.getOrCreateManagerInstance();
        System.out.println("Manager instance ID: " + managerId);





    }
}
