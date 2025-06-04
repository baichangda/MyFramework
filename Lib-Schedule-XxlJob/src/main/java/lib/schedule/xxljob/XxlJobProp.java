package lib.schedule.xxljob;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lib.schedule.xxl-job")
public class XxlJobProp {
    public String adminAddresses;
    public String accessToken;
    public int timeout;
    public String appName;
    public String address;
    public String ip;
    public int port;
    public String logPath;
    public int logRetentionDays;
}
