package cn.bcd.lib.vehicle.command;


import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;

public abstract class Command<T, R> {
    public final T request;
    public final PacketFlag flag;

    public Command(T request, PacketFlag flag) {
        this.request = request;
        this.flag = flag;
    }
    public abstract byte[] requestToBytes();

    public abstract R toResponse(byte[] content);
}
