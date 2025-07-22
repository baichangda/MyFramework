package cn.bcd.app.tool.minio.client.server;

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
