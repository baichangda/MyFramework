package cn.bcd.lib.data.init.nacos;

import lombok.Data;

import java.util.Map;

@Data
public class HostData {
    public String ip;
    public int port;
    public double weight;
    public boolean healthy;
    public boolean enabled;
    public boolean ephemeral;
    public String clusterName;
    public String serviceName;
    public Map<String,String> metadata;
    public int instanceHeartBeatTimeOut;
    public int ipDeleteTimeout;
    public int instanceHeartBeatInterval;
}
