package cn.bcd.app.tool.kafka.client.web.base.support_spring_websocket;

import org.springframework.web.socket.handler.TextWebSocketHandler;

public abstract class PathTextWebSocketHandler extends TextWebSocketHandler {
    public final String[] paths;

    public PathTextWebSocketHandler(String... paths) {
        this.paths = paths;
    }
}
