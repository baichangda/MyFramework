package cn.bcd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * MQTT 服务的 Spring Boot 启动入口。
 */
@ConfigurationPropertiesScan(basePackages = "cn.bcd")
@SpringBootApplication(scanBasePackages = "cn.bcd")
public class Application {

    /**
     * 启动 Spring Boot 应用上下文。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
