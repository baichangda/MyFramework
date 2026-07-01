package cn.bcd.lib.spring.aws.s3;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import software.amazon.awssdk.regions.Region;

@Data
@ConfigurationProperties(prefix = "lib.spring.aws.s3")
public class AwsS3Prop {
    public String endpoint;
    public String region = Region.US_EAST_1.id();
    public String accessKey;
    public String secretKey;
    public boolean forcePathStyle = false;
    public String bucket;
}
