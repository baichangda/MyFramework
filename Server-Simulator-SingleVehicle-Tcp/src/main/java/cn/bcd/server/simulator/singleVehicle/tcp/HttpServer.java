package cn.bcd.server.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.parser.protocol.gb32960.data.Packet;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(name = "singleVehicle", mixinStandardHelpOptions = true)
public class HttpServer implements Runnable {

    static Logger logger= LoggerFactory.getLogger(HttpServer.class);

    @CommandLine.Option(names = {"-p", "--httpServerPort"}, description = "http server port", required = false, defaultValue = "45678", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    int httpServerPort;

    @Override
    public void run() {
        Vertx vertx = Vertx.builder().build();
        io.vertx.core.http.HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route("/*").handler(StaticHandler.create("server/simulator/singleVehicle/tcp")
                .setDefaultContentEncoding("UTF-8")
                .setCachingEnabled(true)
        );
        router.route("/parse").handler(ctx -> {
            ctx.request().bodyHandler(event -> {
                String hex = event.toString();
                ctx.response().putHeader("content-type", "application/json;charset=utf-8");
                try {
                    byte[] bytes = ByteBufUtil.decodeHexDump(hex);
                    try {
                        Packet packet = Packet.read(Unpooled.wrappedBuffer(bytes));
                        String json = JsonUtil.toJson(packet);
                        ctx.response().send(JsonUtil.toJson(Map.of("data", json, "succeed", true)));
                    } catch (Exception ex) {
                        logger.error("parse protocol error:\n{}", hex, ex);
                        ctx.response().send(JsonUtil.toJson(JsonUtil.toJson(Map.of("msg", "解析失败、报文不符合格式", "succeed", false))));
                    }
                } catch (Exception ex) {
                    logger.error("parse hex error:\n{}", hex, ex);
                    ctx.response().send(JsonUtil.toJson(JsonUtil.toJson(Map.of("msg", "解析失败、报文不是16进制格式", "succeed", false))));
                }
            });
        });

        router.route("/ws").handler(ctx -> {
            ctx.request().toWebSocket().onSuccess(webSocket -> {
                String vin = ctx.queryParam("vin").getFirst();
                logger.info("-------------ws open vin[{}]--------------", vin);
                WsSession wsSession = new WsSession(vin,webSocket);
                WsSession prev = WsSession.sessionMap.putIfAbsent(vin, wsSession);
                if (prev != null) {
                    try {
                        webSocket.writeTextMessage(JsonUtil.toJson(new WsOutMsg(999, "车辆[" + vin + "]正在使用中、请更换车辆", false)));
                        wsSession.ws_onClose();
                        WsSession.sessionMap.remove(vin);
                        webSocket.close();
                        logger.info("-------------ws close vin[{}]--------------", vin);
                        return;
                    } catch (Exception ex) {
                        logger.error("error", ex);
                    }
                }
                wsSession.init();
                webSocket.frameHandler(frame -> {
                    switch (frame.type()) {
                        case TEXT -> {
                            String data = frame.textData();
                            try {
                                WsInMsg wsInMsg = JsonUtil.OBJECT_MAPPER.readValue(data, WsInMsg.class);
                                wsSession.ws_onMessage(wsInMsg);
                            } catch (Exception ex) {
                                logger.error("receive ws msg parse json error:\n{}", data);
                            }
                        }
                        case CLOSE -> {
                            try {
                                wsSession.ws_onClose();
                                WsSession.sessionMap.remove(vin);
                                logger.info("-------------ws close vin[{}]--------------", vin);
                            } catch (Exception ex) {
                                logger.error("error", ex);
                            }
                        }
                    }
                });
            }).onFailure(ex -> logger.error("error", ex));
        });

        httpServer.requestHandler(router).listen(11111);
    }
}
