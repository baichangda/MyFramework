package cn.bcd.app.dataProcess.gateway.tcp.v2025;

import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;

public interface DataHandler_v2025 {
    default void init(String vin, VehicleCacheData_v2025 vehicleCacheData) throws Exception{}

    default void destroy(String vin, VehicleCacheData_v2025 vehicleCacheData) throws Exception{};

    void handle(String vin, PacketFlag flag, byte[] data, VehicleCacheData_v2025 vehicleCacheData) throws Exception;
}
