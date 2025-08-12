package cn.bcd.app.dataProcess.parse.v2025;


import cn.bcd.lib.parser.protocol.gb32960.v2025.data.Packet;

public interface DataHandler_v2025 {
    default void init(String vin, Context_v2025 context)  throws Exception {
    }

    default void destroy(String vin, Context_v2025 context)  throws Exception {
    }

    void handle(String vin, Packet packet, Context_v2025 context) throws Exception;
}
