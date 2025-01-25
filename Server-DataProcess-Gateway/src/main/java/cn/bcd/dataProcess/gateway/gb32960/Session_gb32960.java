package cn.bcd.dataProcess.gateway.gb32960;

import cn.bcd.dataProcess.gateway.Protocol;
import cn.bcd.dataProcess.gateway.Session;
import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;

public class Session_gb32960 extends Session {
    static Protocol protocol = Protocol.gb32960;

    public Session_gb32960(String id, Channel channel) {
        super(protocol.type, id, channel);
    }

    public static ConcurrentHashMap<String, Session> getSessionMap() {
        return Protocol.sessionMap[protocol.type];
    }
}
