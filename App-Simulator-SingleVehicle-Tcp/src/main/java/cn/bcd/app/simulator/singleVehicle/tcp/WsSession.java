package cn.bcd.app.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.executor.IdEventExecutorGroup;
import cn.bcd.lib.base.json.JsonUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class WsSession implements Vehicle.Listener {

    private static final Logger logger = LoggerFactory.getLogger(WsSession.class);
    private static final IdEventExecutorGroup EXECUTOR_GROUP =
            new IdEventExecutorGroup(Runtime.getRuntime().availableProcessors());

    public static final ConcurrentHashMap<String, WsSession> sessionMap = new ConcurrentHashMap<>();

    public final String vin;
    private final ServerWebSocket webSocket;
    private final Vehicle vehicle;
    private final EventExecutor executor;
    private boolean closed;

    public WsSession(String vin,
                     int sendPeriod,
                     Function<String, VehicleData> vehicleDataFactory,
                     ServerWebSocket webSocket) {
        this.vin = vin;
        this.executor = EXECUTOR_GROUP.getEventExecutor(vin);
        this.webSocket = webSocket;
        this.vehicle = new Vehicle(vin, sendPeriod, vehicleDataFactory, executor, this);
    }

    public void init() {
        executeTask(() -> sendVehicleData(vehicle.init()));
    }

    public void onWebSocketClose() {
        executeTask(() -> {
            if (closed) {
                return;
            }
            closed = true;
            vehicle.destroy();
        });
    }

    public void onWebSocketMessage(WsInMsg message) {
        executeTask(() -> {
            if (closed) {
                return;
            }
            switch (message.flag()) {
                case 1 -> connectTcp(message.data());
                case 2 -> updateVehicleData(message.data());
                default -> logger.warn("unsupported ws message flag[{}] vin[{}]", message.flag(), vin);
            }
        });
    }

    @Override
    public void onTcpConnected() {
        executeTask(() -> {
            logger.info("-------------tcp connected vin[{}]--------------", vin);
            vehicle.startSending();
            sendWebSocketMessage(new WsOutMsg(1, null, true));
        });
    }

    @Override
    public void onTcpConnectFailed(Throwable cause) {
        executeTask(() -> {
            logger.error("connect tcp failed vin[{}]", vin, cause);
            sendWebSocketMessage(new WsOutMsg(1, cause.getMessage(), false));
        });
    }

    @Override
    public void onTcpDisconnected() {
        executeTask(() -> {
            logger.info("-------------tcp disconnected vin[{}]--------------", vin);
            vehicle.stopSending();
            sendWebSocketMessage(new WsOutMsg(104, null, true));
        });
    }

    @Override
    public void onTcpSend(byte[] data) {
        executeTask(() -> sendWebSocketMessage(
                new WsOutMsg(102, ByteBufUtil.hexDump(data), true)));
    }

    @Override
    public void onTcpReceive(byte[] data) {
        executeTask(() -> sendWebSocketMessage(
                new WsOutMsg(103, ByteBufUtil.hexDump(data), true)));
    }

    private void connectTcp(String address) {
        try {
            TcpAddress tcpAddress = parseTcpAddress(address);
            vehicle.connect(tcpAddress.host(), tcpAddress.port());
        } catch (Exception ex) {
            logger.error("connect tcp address[{}] error", address, ex);
            sendWebSocketMessage(new WsOutMsg(1, ex.getMessage(), false));
        }
    }

    private void updateVehicleData(String json) {
        try {
            VehicleData current = vehicle.vehicleData();
            VehicleData updated = JsonUtil.OBJECT_MAPPER.readValue(json, VehicleData.class);
            if (current == null || current.getClass() != updated.getClass()) {
                throw new IllegalArgumentException("vehicle data protocol version cannot be changed");
            }
            if (!vin.equals(updated.vin)) {
                throw new IllegalArgumentException("vehicle data vin does not match websocket vin");
            }
            vehicle.updateVehicleData(updated);
            sendWebSocketMessage(new WsOutMsg(2, null, true));
        } catch (Exception ex) {
            logger.error("update vehicle data vin[{}] error", vin, ex);
            sendWebSocketMessage(new WsOutMsg(2, null, false));
        }
    }

    private void sendVehicleData(VehicleData vehicleData) {
        sendWebSocketMessage(new WsOutMsg(101, JsonUtil.toJson(vehicleData), true));
    }

    private void sendWebSocketMessage(WsOutMsg message) {
        if (!closed) {
            webSocket.writeTextMessage(JsonUtil.toJson(message))
                    .onFailure(ex -> logger.error("send ws message vin[{}] failed", vin, ex));
        }
    }

    private void executeTask(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                logger.error("execute session task vin[{}] failed", vin, ex);
            }
        });
    }

    static TcpAddress parseTcpAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("tcp address cannot be blank");
        }

        String value = address.trim();
        String host;
        String portText;
        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket <= 1 || closingBracket + 1 >= value.length()
                    || value.charAt(closingBracket + 1) != ':') {
                throw new IllegalArgumentException("invalid bracketed tcp address");
            }
            host = value.substring(1, closingBracket);
            portText = value.substring(closingBracket + 2);
        } else {
            int separator = value.lastIndexOf(':');
            if (separator <= 0 || separator == value.length() - 1
                    || value.substring(0, separator).contains(":")) {
                throw new IllegalArgumentException("tcp address must use host:port");
            }
            host = value.substring(0, separator).trim();
            portText = value.substring(separator + 1).trim();
        }

        if (host.isEmpty()) {
            throw new IllegalArgumentException("tcp host cannot be blank");
        }
        int port = Integer.parseInt(portText);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("tcp port must be between 1 and 65535");
        }
        return new TcpAddress(host, port);
    }

    record TcpAddress(String host, int port) {
    }
}
