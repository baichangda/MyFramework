package cn.bcd.app.dataProcess.parse;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "parse")
public class ParseProp {
    public String id;
    public String topic;
}
