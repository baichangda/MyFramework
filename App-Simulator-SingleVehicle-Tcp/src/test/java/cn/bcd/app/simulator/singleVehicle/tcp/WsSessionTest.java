package cn.bcd.app.simulator.singleVehicle.tcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsSessionTest {

    @Test
    void parseTcpAddressSupportsHostNamesAndIpv6() {
        assertEquals(new WsSession.TcpAddress("gateway.local", 6666),
                WsSession.parseTcpAddress("gateway.local:6666"));
        assertEquals(new WsSession.TcpAddress("::1", 443),
                WsSession.parseTcpAddress("[::1]:443"));
    }

    @Test
    void parseTcpAddressRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> WsSession.parseTcpAddress(""));
        assertThrows(IllegalArgumentException.class, () -> WsSession.parseTcpAddress("localhost"));
        assertThrows(IllegalArgumentException.class, () -> WsSession.parseTcpAddress("::1:443"));
        assertThrows(IllegalArgumentException.class, () -> WsSession.parseTcpAddress("localhost:0"));
        assertThrows(IllegalArgumentException.class, () -> WsSession.parseTcpAddress("localhost:65536"));
    }

    @Test
    void vehicleInitializationIsSynchronous() {
        Vehicle vehicle = new Vehicle("LSJE36096MS140495", 10,
                TestVehicleData::new, null, null);

        TestVehicleData data = (TestVehicleData) vehicle.init();

        assertTrue(data.initialized);
        assertEquals(data, vehicle.vehicleData());
    }

    private static final class TestVehicleData extends VehicleData {
        private boolean initialized;

        private TestVehicleData(String vin) {
            super(vin);
        }

        @Override
        public void init_vehicleRunData() {
            initialized = true;
        }

        @Override
        public byte[] onSend_vehicleRunDataToBytes() {
            return new byte[0];
        }
    }
}
