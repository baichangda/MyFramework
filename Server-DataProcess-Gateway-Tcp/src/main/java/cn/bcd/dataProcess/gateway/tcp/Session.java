package cn.bcd.dataProcess.gateway.tcp;

import io.netty.channel.Channel;

import java.util.concurrent.Future;

public class Session {
    public final int type;
    public final String id;
    public final long createTs;
    public final Channel channel;
    public boolean closed;

    public Session(int type, String id, Channel channel) {
        this.type = type;
        this.id = id;
        this.channel = channel;
        this.createTs = System.currentTimeMillis();
        Session old = Protocol.sessionMap[type].put(id, this);
        if (old != null) {
            old.close();
        }
    }

    public static Session getSession(int type, String id) {
        return Protocol.sessionMap[type].get(id);
    }

    public Future<?> close() {
        Session cur = this;
        return channel.eventLoop().submit(() -> {
            if (!closed) {
                Protocol.sessionMap[type].remove(id, cur);
                //关闭会话
                channel.close();
                closed = true;
            }
        });
    }


}
