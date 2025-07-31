package cn.bcd.lib.vehicle.command;


import cn.bcd.lib.parser.protocol.gb32960.ProtocolVersion;

public abstract class Command<T, R> {
    public final T request;
    public final int flag;
    //协议版本
    public final ProtocolVersion version;

    public Command(T request, int flag, ProtocolVersion version) {
        this.request = request;
        this.flag = flag;
        this.version = version;
    }

    public abstract byte[] toRequestBytes();

    public abstract R toResponse(byte[] content);
}
