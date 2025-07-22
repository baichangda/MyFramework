package cn.bcd.app.data.process.gateway.tcp;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class Session {
    public final String id;
    public final long createTs;
    public final Channel channel;
    public boolean closed;

    public final static ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();

    public Session(String id, Channel channel) {
        this.id = id;
        this.channel = channel;
        this.createTs = System.currentTimeMillis();
        Session old = Session.sessionMap.put(id, this);
        if (old != null) {
            old.close();
        }
    }

    public static Session getSession(String id) {
        return Session.sessionMap.get(id);
    }

    public Future<?> close() {
        Session cur = this;
        return channel.eventLoop().submit(() -> {
            if (!closed) {
                Session.sessionMap.remove(id, cur);
                //关闭会话
                channel.close();
                closed = true;
            }
        });
    }


}
