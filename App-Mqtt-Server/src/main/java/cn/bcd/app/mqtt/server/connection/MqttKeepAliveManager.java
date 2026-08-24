package cn.bcd.app.mqtt.server.connection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MqttKeepAliveManager {

    static final String IDLE_STATE_HANDLER_NAME = "mqttIdleStateHandler";

    public void configure(ChannelHandlerContext context, int keepAliveSeconds) {
        if (keepAliveSeconds == 0) {
            return;
        }
        long readerIdleMillis = keepAliveSeconds * 1500L;
        context.pipeline().addBefore(context.name(), IDLE_STATE_HANDLER_NAME,
                new IdleStateHandler(readerIdleMillis, 0, 0, TimeUnit.MILLISECONDS));
    }
}
