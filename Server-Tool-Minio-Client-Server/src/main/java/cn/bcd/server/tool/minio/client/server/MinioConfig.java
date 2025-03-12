package cn.bcd.server.tool.minio.client.server;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProp.class)
public class MinioConfig {

    static Logger logger = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient(MinioProp minioProp) {
        logger.info("minioProp endpoint[{}] accessKey[{}] secretKey[{}] bucket[{}]",
                minioProp.endpoint, minioProp.accessKey, minioProp.secretKey, minioProp.bucket);
        return MinioClient.builder()
                .endpoint(minioProp.endpoint)
                .credentials(minioProp.accessKey, minioProp.secretKey)
                .build();
    }
}
