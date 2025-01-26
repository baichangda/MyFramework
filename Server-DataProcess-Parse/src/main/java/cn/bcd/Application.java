package cn.bcd;

import cn.bcd.dataProcess.parse.ParseProp;
import cn.bcd.dataProcess.parse.kafka.ext.KafkaProp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author liqi
 */
@EnableConfigurationProperties({ParseProp.class, KafkaProp.class})
@SpringBootApplication(scanBasePackages = {"cn.bcd"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}