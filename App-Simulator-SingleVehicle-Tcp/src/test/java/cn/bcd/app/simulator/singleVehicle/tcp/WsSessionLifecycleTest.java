package cn.bcd.app.simulator.singleVehicle.tcp;

import cn.bcd.app.simulator.singleVehicle.tcp.v2016.VehicleData_v2016;
import cn.bcd.app.simulator.singleVehicle.tcp.v2025.VehicleData_v2025;
import cn.bcd.lib.base.json.JsonUtil;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class WsSessionLifecycleTest {
    private static final String VIN = "LSJE36096MS140495";

    @Test
    void queuedConnectedCallbackCannotRestartClosedSession() throws Exception {
        WsSession session = new WsSession(VIN, 60, VehicleData_v2016::new, null);
        EventExecutor executor = field(session, "executor", EventExecutor.class);
        Vehicle vehicle = field(session, "vehicle", Vehicle.class);
        try {
            session.onWebSocketClose();
            session.onTcpConnected();
            executor.submit(() -> {}).sync();
            assertTrue(field(session, "closed", Boolean.class));
            assertNull(field(vehicle, "sendTask", Object.class));
        } finally {
            executor.submit(vehicle::destroy).sync();
        }
    }

    @Test
    void v2016BrowserUpdateIsAccepted() throws Exception {
        verifyUpdate(VehicleData_v2016::new);
    }

    @Test
    void v2025BrowserUpdateIsAccepted() throws Exception {
        verifyUpdate(VehicleData_v2025::new);
    }

    private static void verifyUpdate(Function<String, VehicleData> factory) throws Exception {
        List<WsOutMsg> messages = new CopyOnWriteArrayList<>();
        ServerWebSocket socket = (ServerWebSocket) Proxy.newProxyInstance(
                ServerWebSocket.class.getClassLoader(), new Class<?>[]{ServerWebSocket.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("writeTextMessage")) {
                        messages.add(JsonUtil.MAPPER.readValue((String) args[0], WsOutMsg.class));
                        return Future.succeededFuture();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        WsSession session = new WsSession(VIN, 60, factory, socket);
        EventExecutor executor = field(session, "executor", EventExecutor.class);
        Vehicle vehicle = field(session, "vehicle", Vehicle.class);
        try {
            session.init();
            executor.submit(() -> {}).sync();
            VehicleData original = vehicle.vehicleData();
            session.onWebSocketMessage(new WsInMsg(2, JsonUtil.toJson(original)));
            executor.submit(() -> {}).sync();
            assertNotSame(original, vehicle.vehicleData());
            assertEquals(VIN, vehicle.vehicleData().vin);
            assertEquals(2, messages.size());
            assertEquals(2, messages.getLast().flag());
            assertTrue(messages.getLast().succeed());
        } finally {
            session.onWebSocketClose();
            executor.submit(() -> {}).sync();
        }
    }

    static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(object));
    }
}
