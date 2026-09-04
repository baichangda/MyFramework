package cn.bcd.app.dataProcess.gateway.mqtt.v2025;

import cn.bcd.lib.spring.data.notify.onlyNotify.vehicleData.VehicleData;
import lombok.Data;

import java.util.Date;

@Data
public class Context_v2025 {
    // 网关接收时间
    public Date receiveTime;

    private VehicleData vehicleData;
    //VehicleOnlineHandler
    public long lastTimeTs;
}
