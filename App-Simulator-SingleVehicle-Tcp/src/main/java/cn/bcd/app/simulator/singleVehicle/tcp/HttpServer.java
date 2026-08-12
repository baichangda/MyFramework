package cn.bcd.app.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.json.JsonUtil;
import io.netty.buffer.ByteBufUtil;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public abstract class HttpServer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
    private static final long MAX_HTTP_BODY_SIZE = 1024 * 1024;
    private static final int MAX_WEBSOCKET_MESSAGE_SIZE = 1024 * 1024;

    private final Function<String, VehicleData> vehicleDataFactory;

    @CommandLine.ParentCommand
    Starter starter;

    protected HttpServer(Function<String, VehicleData> vehicleDataFactory) {
        this.vehicleDataFactory = vehicleDataFactory;
    }

    public abstract String hexToJson(byte[] hex);

    public abstract String jsonToHex(String json) throws Exception;

    @Override
    public void run() {
        validateOptions();

        Vertx vertx = Vertx.builder().build();
        io.vertx.core.http.HttpServer httpServer = vertx.createHttpServer(
                new HttpServerOptions()
                        .setCompressionSupported(true)
                        .setIdleTimeoutUnit(TimeUnit.SECONDS)
                        .setIdleTimeout(60)
                        .setMaxWebSocketMessageSize(MAX_WEBSOCKET_MESSAGE_SIZE)
        );
        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create(LoggerFormat.SHORT));

        BodyHandler bodyHandler = BodyHandler.create().setBodyLimit(MAX_HTTP_BODY_SIZE);
        router.post("/parse").handler(bodyHandler).handler(ctx -> {
            String hex = ctx.body().asString();
            ctx.response().putHeader("content-type", "application/json;charset=utf-8");
            try {
                byte[] bytes = ByteBufUtil.decodeHexDump(hex);
                try {
                    String json = hexToJson(bytes);
                    ctx.response().send(JsonUtil.toJson(Map.of("data", json, "succeed", true)));
                } catch (Exception ex) {
                    logger.error("parse protocol error:\n{}", hex, ex);
                    ctx.response().send(JsonUtil.toJson(
                            Map.of("msg", "解析失败、报文不符合协议格式", "succeed", false)));
                }
            } catch (Exception ex) {
                logger.error("parse hex error:\n{}", hex, ex);
                ctx.response().send(JsonUtil.toJson(
                        Map.of("msg", "解析失败、报文不是16进制格式", "succeed", false)));
            }
        });

        router.post("/deParse").handler(bodyHandler).handler(ctx -> {
            String json = ctx.body().asString();
            ctx.response().putHeader("content-type", "application/json;charset=utf-8");
            try {
                String hex = jsonToHex(json);
                ctx.response().send(JsonUtil.toJson(Map.of("data", hex, "succeed", true)));
            } catch (Exception ex) {
                logger.error("deParse protocol error:\n{}", json, ex);
                ctx.response().send(JsonUtil.toJson(
                        Map.of("msg", "反解析失败、json数据不符合协议格式", "succeed", false)));
            }
        });

        router.route("/ws").handler(ctx -> {
            List<String> vinValues = ctx.queryParam("vin");
            if (vinValues.isEmpty() || !isValidVin(vinValues.getFirst())) {
                ctx.response().setStatusCode(400).end("invalid vin");
                return;
            }
            String vin = vinValues.getFirst();

            ctx.request().toWebSocket().onSuccess(webSocket -> {
                logger.info("-------------ws open vin[{}]--------------", vin);
                WsSession session = new WsSession(vin, starter.sendPeriod, vehicleDataFactory, webSocket);
                WsSession previous = WsSession.sessionMap.putIfAbsent(vin, session);
                if (previous != null) {
                    webSocket.writeTextMessage(JsonUtil.toJson(
                                    new WsOutMsg(999, "车辆[" + vin + "]正在使用中、请更换车辆", false)))
                            .onComplete(ignored -> webSocket.close());
                    return;
                }

                webSocket.closeHandler(ignored -> {
                    session.onWebSocketClose();
                    WsSession.sessionMap.remove(vin, session);
                    logger.info("-------------ws close vin[{}]--------------", vin);
                });
                webSocket.exceptionHandler(ex -> {
                    logger.error("ws connection vin[{}] error", vin, ex);
                    webSocket.close();
                });
                webSocket.textMessageHandler(data -> {
                    try {
                        WsInMsg message = JsonUtil.OBJECT_MAPPER.readValue(data, WsInMsg.class);
                        session.onWebSocketMessage(message);
                    } catch (Exception ex) {
                        logger.error("receive ws msg parse json error:\n{}", data, ex);
                    }
                });
                session.init();
            }).onFailure(ex -> logger.error("upgrade websocket failed vin[{}]", vin, ex));
        });

        router.route("/*").handler(StaticHandler.create("app/simulator/singleVehicle/tcp")
                .setDefaultContentEncoding("UTF-8")
                .setCachingEnabled(true));

        httpServer.requestHandler(router).listen(starter.httpServerPort)
                .onSuccess(server -> logger.info("http server started port[{}]", server.actualPort()))
                .onFailure(ex -> {
                    logger.error("start http server port[{}] failed", starter.httpServerPort, ex);
                    vertx.close();
                });
    }

    private void validateOptions() {
        if (starter.httpServerPort < 1 || starter.httpServerPort > 65_535) {
            throw new IllegalArgumentException("http server port must be between 1 and 65535");
        }
        if (starter.sendPeriod <= 0) {
            throw new IllegalArgumentException("send period must be greater than zero");
        }
    }

    private static boolean isValidVin(String vin) {
        return vin != null && vin.matches("[A-HJ-NPR-Z0-9]{17}");
    }
}
