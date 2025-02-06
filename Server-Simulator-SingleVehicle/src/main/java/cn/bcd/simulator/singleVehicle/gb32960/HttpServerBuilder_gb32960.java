package cn.bcd.simulator.singleVehicle.gb32960;

import cn.bcd.base.exception.BaseException;
import cn.bcd.base.json.JsonUtil;
import cn.bcd.parser.protocol.gb32960.data.Packet;
import cn.bcd.simulator.singleVehicle.HttpServer;
import cn.bcd.simulator.singleVehicle.HttpServerBuilder;
import cn.bcd.simulator.singleVehicle.WsInMsg;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.util.Headers;
import io.undertow.util.MimeMappings;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class HttpServerBuilder_gb32960 implements HttpServerBuilder {
    static Logger logger = LoggerFactory.getLogger(HttpServerBuilder_gb32960.class);

    public void build(PathHandler pathHandler) {
        try (ResourceManager resourceManager = new ClassPathResourceManager(HttpServer.class.getClassLoader(), "http/gb32960")) {
            MimeMappings mimeMappings = MimeMappings.builder().addMapping("html", "text/html;charset=utf-8").build();
            ResourceHandler resourceHandler = new ResourceHandler(resourceManager).setMimeMappings(mimeMappings);
            pathHandler.addPrefixPath("/gb32960", resourceHandler);
            pathHandler.addExactPath("/parse/gb32960", exchange -> {
                String hex = exchange.getQueryParameters().get("hex").getFirst();
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json;charset=utf-8");
                try {
                    byte[] bytes = ByteBufUtil.decodeHexDump(hex);
                    try {
                        Packet packet = Packet.read(Unpooled.wrappedBuffer(bytes));
                        String json = JsonUtil.toJson(packet);
                        exchange.getResponseSender().send(JsonUtil.toJson(Map.of("data", json, "succeed", true)));
                    } catch (Exception ex) {
                        logger.error("parse protocol error:\n{}", hex, ex);
                        exchange.getResponseSender().send(JsonUtil.toJson(Map.of("msg", "解析失败、报文不符合格式", "succeed", false)));
                    }
                } catch (Exception ex) {
                    logger.error("parse hex error:\n{}", hex, ex);
                    exchange.getResponseSender().send(JsonUtil.toJson(Map.of("msg", "解析失败、报文不是16进制格式", "succeed", false)));
                }
            });

            WebSocketProtocolHandshakeHandler webSocketProtocolHandshakeHandler = new WebSocketProtocolHandshakeHandler((WebSocketConnectionCallback) (exchange, channel) -> {
                String vin = exchange.getRequestParameters().get("vin").getFirst();
                WsSession_gb32960 wsSession= new WsSession_gb32960(channel, vin);
                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                        String data = message.getData();
                        try {
                            WsInMsg wsInMsg = JsonUtil.OBJECT_MAPPER.readValue(data, WsInMsg.class);
                            wsSession.ws_onMsg(wsInMsg);
                        } catch (IOException ex) {
                            logger.error("receive ws msg parse json error:\n{}", data);
                        }
                    }

                    @Override
                    protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) {
                        wsSession.ws_onClose();
                    }
                });
                channel.resumeReceives();
            });
            pathHandler.addExactPath("/ws/gb32960", webSocketProtocolHandshakeHandler);
        } catch (IOException ex) {
            throw BaseException.get(ex);
        }
    }
}
