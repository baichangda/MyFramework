package cn.bcd.dataProcess.gateway.tcp;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public enum Protocol {
    gb32960(0),
            ;
    public final int type;

    Protocol(int type) {
        this.type = type;
    }


    public final static int protocolCount = Protocol.values().length;

    public final static ConcurrentHashMap<String, Session>[] sessionMap = new ConcurrentHashMap[protocolCount];
    static {
        sessionMap[Protocol.gb32960.type] = new ConcurrentHashMap<>();
    }
}
