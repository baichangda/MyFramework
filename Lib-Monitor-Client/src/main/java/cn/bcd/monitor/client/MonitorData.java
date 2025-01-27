package cn.bcd.monitor.client;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;


@Getter
@Setter
public class MonitorData {

    public SystemData systemData;

    public Map<String, Object> extData;

}
