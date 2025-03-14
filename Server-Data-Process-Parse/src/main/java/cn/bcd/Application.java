package cn.bcd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

/**
 * @author liqi
 */

@SpringBootApplication(scanBasePackages = {"cn.bcd"},
        exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class}
)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}