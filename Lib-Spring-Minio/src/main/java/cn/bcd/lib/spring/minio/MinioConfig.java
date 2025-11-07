package cn.bcd.lib.spring.minio;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "lib.spring.minio.endpoint")
@EnableConfigurationProperties(MinioProp.class)
public class MinioConfig {
    @Bean
    public MinioClient minioClient(MinioProp minioProp) {
        return MinioClient.builder()
                .endpoint(minioProp.endpoint)
                .credentials(minioProp.accessKey, minioProp.secretKey)
                .build();
    }
}
