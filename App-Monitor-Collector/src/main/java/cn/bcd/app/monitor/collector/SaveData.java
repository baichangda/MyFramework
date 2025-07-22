package cn.bcd.app.monitor.collector;


import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.monitor.client.MonitorData;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveData {
    public String serverId;
    public int serverType;
    public long batch;
    public int status;
    public String systemData;
    public String extData;

    public SaveData(ServerData serverData, long batch, MonitorData monitorData) {
        this.serverId = serverData.serverId;
        this.serverType = serverData.serverType;
        this.batch = batch;
        if (monitorData == null) {
            this.status = 1;
        } else {
            this.status = 0;
            this.systemData = JsonUtil.toJson(monitorData.systemData);
            this.extData = monitorData.extData == null ? null : JsonUtil.toJson(monitorData.extData);
        }
    }
}
