package cn.bcd.app.dataProcess.gateway.tcp.v2016;

import cn.bcd.app.dataProcess.gateway.tcp.Session;
import cn.bcd.lib.data.notify.onlyNotify.vehicleData.VehicleData;
import io.netty.channel.ChannelHandlerContext;
import lombok.Data;

import java.util.Date;

@Data
public class VehicleCacheData_v2016 {
    public ChannelHandlerContext ctx;
    /****************************************通用缓存*********************************************/
    public VehicleData vehicleData;
    // 网关接收时间
    public Date receiveTime;
    //SessionHandler
    public Session session;
    //VehicleOnlineHandler
    public long lastTimeTs;
}
