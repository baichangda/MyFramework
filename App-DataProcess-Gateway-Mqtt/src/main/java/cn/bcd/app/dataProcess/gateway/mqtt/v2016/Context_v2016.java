package cn.bcd.app.dataProcess.gateway.mqtt.v2016;

import cn.bcd.lib.data.notify.onlyNotify.vehicleData.VehicleData;
import lombok.Data;

import java.util.Date;

@Data
public class Context_v2016 {

    // 网关接收时间
    public Date receiveTime;

    /****************************************通用缓存*********************************************/
    private VehicleData vehicleData;
    /**********************************VehicleOnlineHandler**************************************/
    public long lastTimeTs;
}
