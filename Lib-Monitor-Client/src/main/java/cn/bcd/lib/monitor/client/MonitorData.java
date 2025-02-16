package cn.bcd.lib.monitor.client;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;


@Getter
@Setter
public class MonitorData {
    public String serverId;
    public int serverType;
    public long batch;
    public SystemData systemData;
    public Map<String, Object> extData;
}
