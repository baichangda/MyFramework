package cn.bcd.app.dataProcess.gateway.mqtt.v2016;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;

public interface DataHandler_v2016 {
    default void init(String vin, Context_v2016 context) throws Exception{}

    default void destroy(String vin, Context_v2016 context) throws Exception{};

    void handle(String vin, PacketFlag flag, byte[] data, Context_v2016 context) throws Exception;
}
