package cn.bcd.lib.spring.database.jdbc.backup.mysql;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "lib.spring.database.jdbc.backup.mysql")
public class MysqlBackupProp {
    public String host;
    public int port;
    public String username;
    public String password;
    public String database;
    public int maxFileNum;
    //定时任务备份cron表达式
    public String cron;
}
