package cn.bcd.monitor.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MonitorConfig {

    @Autowired
    List<MonitorLog> monitorLogs;



}
