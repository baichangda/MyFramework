package cn.bcd.lib.spring.database.jdbc.backup.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(MysqlBackupProp.class)
@ConditionalOnProperty("lib.spring.database.jdbc.backup.mysql.host")
public class MysqlBackupSchedule {

    Logger logger = LoggerFactory.getLogger(MysqlBackupSchedule.class);

    @Autowired
    MysqlBackupProp mysqlBackupProp;

    public MysqlBackupSchedule() {
    }

    @Scheduled(cron = "${lib.spring.database.jdbc.backup.mysql.cron}")
    public void backup() {
        MysqlBackupUtil.backup_disk(mysqlBackupProp.host,
                mysqlBackupProp.port,
                mysqlBackupProp.username,
                mysqlBackupProp.password,
                mysqlBackupProp.database,
                "temp",
                mysqlBackupProp.maxFileNum);
    }
}


