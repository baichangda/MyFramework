package cn.bcd.server.simulator.singleVehicle.tcp;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class WsSession<T> {
    static Logger logger = LoggerFactory.getLogger(WsSession.class);
    static NioEventLoopGroup tcp_workerGroup = new NioEventLoopGroup();
    private ScheduledExecutorService pool;
    public final WebSocketChannel wsChannel;
    public Channel channel;
    public volatile T sample;
    public final Class<T> sampleClazz;
    public volatile boolean closed;

    public WsSession(WebSocketChannel wsChannel, Object... args) {
        this.wsChannel = wsChannel;
        this.sample = initSample(args);
        this.sampleClazz = (Class<T>) this.sample.getClass();
        this.closed = false;
        ws_sendSample();
    }

    public synchronized void ws_onClose() {
        closed = true;
        if (pool != null) {
            pool.shutdown();
        }
        if (channel != null) {
            channel.close();
        }
    }

    public synchronized void ws_sendSample() {
        ws_send(new WsOutMsg(101, JsonUtil.toJson(sample), true));
    }

    /**
     * 当数据更新时候
     *
     * @return true代表需要更新客户端、false代表不需要更新客户端
     */
    public abstract boolean ws_onSampleUpdate();

    public synchronized void ws_onMsg(WsInMsg inMsg) {
        switch (inMsg.flag()) {
            case 1 -> {
                String[] split = inMsg.data().split(":");
                try {
                    tcp_connect(split[0], Integer.parseInt(split[1]));
                    tcp_startSendRunData();
                    ws_send(new WsOutMsg(1, null, true));
                } catch (Exception ex) {
                    logger.error("connect tcp address[{}] error", inMsg.data(), ex);
                    ws_send(new WsOutMsg(1, null, false));
                }
            }
            case 2 -> {
                try {
                    sample = JsonUtil.OBJECT_MAPPER.readValue(inMsg.data(), sampleClazz);
                    if (ws_onSampleUpdate()) {
                        ws_send(new WsOutMsg(2, JsonUtil.toJson(sample), true));
                    } else {
                        ws_send(new WsOutMsg(2, null, true));
                    }
                } catch (IOException ex) {
                    logger.error("error", ex);
                    ws_send(new WsOutMsg(2, null, false));
                }
            }
        }
    }


    public synchronized void tcp_connect(String host, int port) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(tcp_workerGroup);
        bootstrap.channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                initSocketChannel(ch);
            }
        });
        if (channel != null) {
            channel.close();
            channel = null;
        }
        try {
            channel = bootstrap.connect(host, port).sync().channel();
        } catch (InterruptedException e) {
            throw BaseException.get(e);
        }
    }

    public synchronized void tcp_onDisConnect() {
        if (pool != null) {
            pool.shutdown();
        }
        pool = null;
        channel = null;
        ws_send(new WsOutMsg(104, null, true));
    }

    public synchronized void tcp_onMsg(ByteBuf byteBuf) {
        ws_send(new WsOutMsg(103, ByteBufUtil.hexDump(byteBuf), true));
    }

    public synchronized void ws_send(WsOutMsg outMsg) {
        if (!closed) {
            try {
                WebSockets.sendTextBlocking(JsonUtil.toJson(outMsg), wsChannel);
            } catch (IOException e) {
                throw BaseException.get(e);
            }
        }
    }

    public synchronized void tcp_sendRunData() {
        ByteBuf buffer = toByteBuf(sample, System.currentTimeMillis());
        String hex = ByteBufUtil.hexDump(buffer);
        try {
            channel.writeAndFlush(buffer).sync();
        } catch (InterruptedException e) {
            throw BaseException.get(e);
        }
        ws_send(new WsOutMsg(102, hex, true));
    }

    public synchronized void tcp_startSendRunData() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
        pool = Executors.newSingleThreadScheduledExecutor();
        pool.scheduleAtFixedRate(this::tcp_sendRunData, 1, 10, TimeUnit.SECONDS);
    }

    public abstract void initSocketChannel(SocketChannel sc);

    public abstract T initSample(Object... args);

    public abstract ByteBuf toByteBuf(T sample, long ts);

}
