package cn.bcd.lib.vehicle.command;


public abstract class Command<T, R> {
    public final T request;
    public final int flag;
    /**
     * 协议版本
     * {@link cn.bcd.lib.parser.protocol.gb32960.Const#protocol_version_2016}
     * {@link cn.bcd.lib.parser.protocol.gb32960.Const#protocol_version_2025}
     */
    public final int version;

    public Command(T request, int flag) {
        this.request = request;
        this.flag = flag;
    }

    public abstract byte[] toRequestBytes();

    public abstract R toResponse(byte[] content);
}
