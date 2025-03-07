package cn.bcd.server.data.process.gateway.mqtt;

public enum Protocol {
    gb32960(0),
            ;
    public final int type;

    Protocol(int type) {
        this.type = type;
    }
    public final static int protocolCount = Protocol.values().length;
}
