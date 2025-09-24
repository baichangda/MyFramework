package cn.bcd.lib.schedule.xxljob;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(prefix = "lib.schedule.xxljob.adminAddresses")
@EnableConfigurationProperties(XxljobProp.class)
@Configuration
public class XxljobConfig {
    static Logger logger= LoggerFactory.getLogger(XxljobConfig.class);
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxljobProp xxlJobProp) {
        logger.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(xxlJobProp.adminAddresses);
        xxlJobSpringExecutor.setAppname(xxlJobProp.appName);
        xxlJobSpringExecutor.setAddress(xxlJobProp.address);
        xxlJobSpringExecutor.setIp(xxlJobProp.ip);
        xxlJobSpringExecutor.setPort(xxlJobProp.port);
        xxlJobSpringExecutor.setAccessToken(xxlJobProp.accessToken);
        xxlJobSpringExecutor.setTimeout(xxlJobProp.timeout);
        xxlJobSpringExecutor.setLogPath(xxlJobProp.logPath);
        xxlJobSpringExecutor.setLogRetentionDays(xxlJobProp.logRetentionDays);
        return xxlJobSpringExecutor;
    }
}
