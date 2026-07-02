package cn.bcd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;

@SpringBootApplication(scanBasePackages = {"cn.bcd"},
        exclude = {MongoAutoConfiguration.class}
)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}