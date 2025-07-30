package cn.bcd.app.data.process.parse;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;

public interface DataHandler_gb32960 {
    default void init(String vin, Context_gb32960 context)  throws Exception {
    }

    default void destroy(String vin, Context_gb32960 context)  throws Exception {
    }

    void handle(String vin, Packet packet, Context_gb32960 context) throws Exception;
}
