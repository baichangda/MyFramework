package cn.bcd.lib.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.minio")
public class MinioProp {
    public String endpoint;
    public String accessKey;
    public String secretKey;
    public String bucket;
}
