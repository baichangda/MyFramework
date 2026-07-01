package cn.bcd.lib.spring.aws.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@ConditionalOnProperty(value = "lib.spring.minio.endpoint")
@EnableConfigurationProperties(AwsS3Prop.class)
public class AwsS3Config {
    @Bean
    public S3Client s3Client(AwsS3Prop awsS3Prop) {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsS3Prop.accessKey, awsS3Prop.secretKey)))
                .endpointOverride(URI.create(awsS3Prop.endpoint))
                .region(Region.of(awsS3Prop.region))
                .forcePathStyle(true)
                .build();
    }
}
