package cn.bcd.lib.data.init.nacos;

import lombok.Data;

@Data
public class ListInstanceData {
    public String name;
    public String groupName;
    public String clusters;
    public int cacheMillis;
    public HostData[] hosts;
    public long lastRefTime;
    public String checksum;
    public boolean allIPs;
    public boolean reachProtectionThreshold;
    public boolean valid;
}
