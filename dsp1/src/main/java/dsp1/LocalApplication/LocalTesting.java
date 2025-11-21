package dsp1.LocalApplication;
import java.io.File;

public class LocalTesting{
    public static void main(String[] args) {
        LocalApplication.CheckPucketExistence();// - Done
        //LocalApplication.listManagerInstances(); - Fail
        //LocalApplication.uploadFileToS3(LocalApplication.S3_BUCKET_NAME,"input.txt", new File("src/main/resources/input.txt")); - Fail
    }
}