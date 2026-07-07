package cn.bcd.lib.spring.aws.s3;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;

public class TestRustSeaweedfs {
    static {
        AwsS3Util.awsS3Prop = new AwsS3Prop();
        AwsS3Util.awsS3Prop.bucket = "test";
        AwsS3Util.s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        "35D94UXJRC7SCFW9MC9F"
                        , "ydooZGVkNkTH3fkVGApnBxDLeXUn1mJ4bheiH7A0no")))
                .endpointOverride(URI.create("https://www.baicd.fun:18333"))
                .region(Region.of(AwsS3Util.awsS3Prop.region))
                .forcePathStyle(true)
                .build();
    }

    @Test
    public void testListObject() {
        List<String> strings = AwsS3Util.listObjects("", true);
        for (String string : strings) {
            System.out.println(string);
        }
    }

    @Test
    public void testPutObject() {
        AwsS3Util.putObject("", Paths.get("d:/file/test1.xlsx"));
    }

    @Test
    public void testPutDir() {
        AwsS3Util.putDir("", true, Paths.get("d:/file"));
    }
}
