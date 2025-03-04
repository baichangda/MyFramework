package cn.bcd.server.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.executor.SingleThreadExecutor;
import cn.bcd.lib.base.executor.SingleThreadExecutorGroup;
import cn.bcd.lib.base.json.JsonUtil;
import io.netty.buffer.ByteBufUtil;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public class WsSession {
    static Logger logger = LoggerFactory.getLogger(WsSession.class);

    public final String vin;
    public final ServerWebSocket channel;
    public final Vehicle vehicle;
    public final SingleThreadExecutor executor;

    public ScheduledFuture<?> scheduledFuture;

    boolean closed;

    public final static ConcurrentHashMap<String, WsSession> sessionMap = new ConcurrentHashMap<>();

    final static SingleThreadExecutorGroup executorGroup = new SingleThreadExecutorGroup("vehicleWorker", Runtime.getRuntime().availableProcessors(), 0, true, null);

    static {
        try {
            executorGroup.init();
        } catch (Exception ex) {
            logger.error("error", ex);
        }
    }

    public WsSession(String vin, ServerWebSocket channel) {
        this.vin = vin;
        this.executor = executorGroup.getExecutor(vin);
        this.channel = channel;
        this.vehicle = new Vehicle(vin, executor);
        this.closed = false;
    }

    public void init() {
        executeTask(() -> {
            this.vehicle.init();
            ws_sendVehicleData(vehicle.vehicleData);
        });
    }

    public void ws_onClose() {
        executeTask(() -> {
            if (!closed) {
                closed = true;
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(false);
                    scheduledFuture = null;
                }
                if (vehicle != null) {
                    vehicle.disconnect();
                }
            }
        });
    }

    public void ws_onMessage(WsInMsg inMsg) {
        executeTask(() -> {
            switch (inMsg.flag()) {
                case 1 -> {
                    String[] split = inMsg.data().split(":");
                    vehicle.connect(split[0],
                                    Integer.parseInt(split[1]),
                                    this::tcp_onConnected,
                                    this::tcp_onDisConnected,
                                    this::tcp_onSend,
                                    this::tcp_onReceive,
                                    this::vehicle_onDataUpdate)
                            .exceptionally(ex -> {
                                logger.error("connect tcp address[{}] error", inMsg.data(), ex);
                                ws_send(new WsOutMsg(1, null, false));
                                return null;
                            });
                }
                case 2 -> {
                    try {
                        vehicle.vehicleData = JsonUtil.OBJECT_MAPPER.readValue(inMsg.data(), VehicleData.class);
                        ws_send(new WsOutMsg(2, null, true));
                    } catch (IOException ex) {
                        logger.error("error", ex);
                        ws_send(new WsOutMsg(2, null, false));
                    }
                }
            }
        });
    }


    public void tcp_onConnected() {
        executeTask(() -> {
            logger.info("-------------tcp connected vin[{}]--------------", vin);
            ws_send(new WsOutMsg(1, null, true));
        });
    }

    public void tcp_onDisConnected() {
        executeTask(() -> {
            logger.info("-------------tcp disconnected vin[{}]--------------", vin);
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
            ws_send(new WsOutMsg(104, null, true));
        });
    }

    public void tcp_onReceive(byte[] data) {
        executeTask(() -> {
            ws_send(new WsOutMsg(103, ByteBufUtil.hexDump(data), true));
        });
    }

    public void tcp_onSend(byte[] data) {
        executeTask(() -> {
            ws_send(new WsOutMsg(102, ByteBufUtil.hexDump(data), true));
        });
    }

    public void vehicle_onDataUpdate(VehicleData vehicleData) {
        executeTask(() -> ws_sendVehicleData(vehicleData));
    }

    private void ws_sendVehicleData(VehicleData vehicleData) {
        ws_send(new WsOutMsg(101, JsonUtil.toJson(vehicleData), true));
    }

    private void ws_send(WsOutMsg outMsg) {
        if (!closed) {
            channel.writeTextMessage(JsonUtil.toJson(outMsg));
        }
    }

    private void executeTask(Runnable runnable) {
        executor.execute(runnable);
    }
}
