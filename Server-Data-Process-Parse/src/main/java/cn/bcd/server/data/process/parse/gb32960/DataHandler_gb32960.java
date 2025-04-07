package cn.bcd.server.data.process.parse.gb32960;

import cn.bcd.lib.parser.protocol.gb32960.data.Packet;

public interface DataHandler_gb32960 {
    default void init(String vin, Context_gb32960 context)  throws Exception {
    }

    default void destroy(String vin, Context_gb32960 context)  throws Exception {
    }

    void handle(String vin, Packet packet, Context_gb32960 context) throws Exception;
}
