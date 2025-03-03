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
import java.util.concurrent.TimeUnit;

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
        this.vehicle = new Vehicle(vin);

        this.closed = false;
    }

    public void init() {
        executeTask(() -> {
            this.vehicle.init();
            ws_sendVehicleData();
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
                    try {
                        tcp_connect(split[0], Integer.parseInt(split[1]));
                    } catch (Exception ex) {
                        logger.error("connect tcp address[{}] error", inMsg.data(), ex);
                        ws_send(new WsOutMsg(1, null, false));
                    }
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
            tcp_startSendRunData();
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

    public void tcp_onMessage(int type, byte[] data) {
        executeTask(() -> {
            if (type == 1) {
                ws_send(new WsOutMsg(102, ByteBufUtil.hexDump(data), true));
            } else {
                ws_send(new WsOutMsg(103, ByteBufUtil.hexDump(data), true));
            }
        });
    }

    private void ws_sendVehicleData() {
        executeTask(() -> {
            ws_send(new WsOutMsg(101, JsonUtil.toJson(vehicle.vehicleData), true));
        });
    }

    private void tcp_connect(String host, int port) {
        vehicle.connect(host, port, this::tcp_onConnected, this::tcp_onDisConnected, this::tcp_onMessage);
    }

    private void ws_send(WsOutMsg outMsg) {
        executeTask(() -> {
            if (!closed) {
                channel.writeTextMessage(JsonUtil.toJson(outMsg));
            }
        });
    }

    private void tcp_sendRunData() {
        executeTask(() -> {
            if (channel.isClosed()) {
                ws_onClose();
            } else {
                byte[] bytes = vehicle.send_vehicleRunData();
                ws_send(new WsOutMsg(102, ByteBufUtil.hexDump(bytes), true));
            }
        });
    }

    private void tcp_startSendRunData() {
        executeTask(() -> {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
            this.scheduledFuture = executor.scheduleAtFixedRate(this::tcp_sendRunData, 1, 10, TimeUnit.SECONDS);
        });
    }

    private void executeTask(Runnable runnable) {
        executor.execute(runnable);
    }
}
