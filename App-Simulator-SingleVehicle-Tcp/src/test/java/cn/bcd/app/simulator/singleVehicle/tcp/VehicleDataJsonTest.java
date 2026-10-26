package cn.bcd.app.simulator.singleVehicle.tcp;

import cn.bcd.app.simulator.singleVehicle.tcp.v2016.VehicleData_v2016;
import cn.bcd.app.simulator.singleVehicle.tcp.v2025.VehicleData_v2025;
import cn.bcd.lib.base.json.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleDataJsonTest {
    private static final String VIN = "LSJE36096MS140495";

    @Test
    void v2016DataCanBeReturnedByTheBrowserAndSent() {
        verifyRoundTrip(new VehicleData_v2016(VIN));
    }

    @Test
    void v2025DataCanBeReturnedByTheBrowserAndSent() {
        verifyRoundTrip(new VehicleData_v2025(VIN));
    }

    private static void verifyRoundTrip(VehicleData original) {
        original.init();
        String json = JsonUtil.toJson(original);
        VehicleData decoded = JsonUtil.MAPPER.readValue(json, VehicleData.class);
        assertEquals(original.getClass(), decoded.getClass());
        assertEquals(VIN, decoded.vin);
        assertEquals(json, JsonUtil.toJson(decoded));
        assertTrue(decoded.onSend_vehicleRunDataToBytes().length > 25);
    }
}
