package cn.bcd.app.dataProcess.gateway.tcp.v2025;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.spring.data.init.vehicle.VehicleDataInit;
import cn.bcd.lib.spring.data.notify.onlyNotify.vehicleData.VehicleData;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class DataInboundHandler_v2025 extends ChannelInboundHandlerAdapter {

    public final List<DataHandler_v2025> handlers;

    static Logger logger = LoggerFactory.getLogger(DataInboundHandler_v2025.class);

    String vin;
    Context_v2025 vehicleCacheData;


    public DataInboundHandler_v2025(List<DataHandler_v2025> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Date receiveTime = new Date();
        //读取数据
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        vin = PacketUtil.getVin(bytes);
        PacketFlag flag = PacketUtil.getPacketFlag(bytes);

        logger.info("receive msg vin[{}] receiverTime[{}] flag[{}]:\n{}", vin, DateZoneUtil.dateToStr_yyyyMMddHHmmss(receiveTime), flag, ByteBufUtil.hexDump(bytes));

        //判断初始化
        if (vehicleCacheData == null) {
            //车辆信息
            VehicleData vehicleData = VehicleDataInit.vin_vehicleData.get(vin);
            if (vehicleData == null) {
                logger.warn("vin[{}] not exists、close conn", vin);
                ctx.close();
                return;
            }
            //初始化缓存
            vehicleCacheData = new Context_v2025();
            vehicleCacheData.ctx = ctx;
            vehicleCacheData.vehicleData = vehicleData;
            vehicleCacheData.receiveTime = receiveTime;

            //初始化handler
            for (DataHandler_v2025 handler : handlers) {
                handler.init(vin, vehicleCacheData);
            }
        } else {
            //保存接收时间
            vehicleCacheData.receiveTime = receiveTime;
        }

        //处理数据
        for (DataHandler_v2025 handler : handlers) {
            handler.handle(vin, flag, bytes, vehicleCacheData);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (vehicleCacheData != null) {
            //销毁
            for (DataHandler_v2025 handler : handlers) {
                handler.destroy(vin, vehicleCacheData);
            }
        }
    }
}
