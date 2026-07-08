package cn.bcd.lib.spring.aws.s3;

import cn.bcd.lib.base.util.FileUtil;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TestRustFs {
    static {
        AwsS3Util.awsS3Prop = new AwsS3Prop();
        AwsS3Util.awsS3Prop.bucket = "test";
        AwsS3Util.s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        "yyLJJhWYR5oSSTgGXwgP"
                        , "tDe7I9vHaxP2pg0NgmmRLTtx7arGKZZ4G4PX8E9O")))
                .endpointOverride(URI.create("https://www.baicd.fun:19000"))
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
        AwsS3Util.putDir("", false, Paths.get("d:/file"));
    }

    @Test
    public void testPutObjectAndGetObject() {
        String dir = "d:/file";
        List<Path> fileList = FileUtil.listDir(false, dir);
        for (Path path : fileList) {
            AwsS3Util.putObject("", path);
        }
        for (Path path : fileList) {
            String fileName = path.getFileName().toString();
            String destFilePath;
            int i = fileName.indexOf(".");
            if (i == -1) {
                destFilePath = dir + "/" + fileName + "-unpack";
            } else {
                destFilePath = dir + "/" + fileName.substring(0, i) + "-unpack" + fileName.substring(i);
            }
            AwsS3Util.getObject(fileName, destFilePath);
        }
    }
}
