package cn.bcd.server.business.process.backend.base.support_minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProp {
    public String endpoint;
    public String accessKey;
    public String secretKey;
    public String bucket;
}
