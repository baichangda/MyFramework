package cn.bcd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan(basePackages = "cn.bcd")
@SpringBootApplication(scanBasePackages = "cn.bcd")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
