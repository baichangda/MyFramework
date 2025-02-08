package cn.bcd.simulator.singleVehicle.tcp.gb32960;

import cn.bcd.parser.protocol.gb32960.Const;
import cn.bcd.parser.protocol.gb32960.data.Packet;
import cn.bcd.parser.protocol.gb32960.data.VehicleRunData;
import cn.bcd.simulator.singleVehicle.tcp.TcpClientHandler;
import cn.bcd.simulator.singleVehicle.tcp.WsSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.undertow.websockets.core.WebSocketChannel;

import java.util.Date;

public class WsSession_gb32960 extends WsSession<Packet> {
    private final static String hex = Const.sample_vehicleRunData;

    public WsSession_gb32960(WebSocketChannel wsChannel, Object... args) {
        super(wsChannel, args);
    }

    public void initSocketChannel(SocketChannel sc) {
        sc.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
        sc.pipeline().addLast(new TcpClientHandler(this));
    }

    public Packet initSample(Object... args) {
        byte[] bytes = ByteBufUtil.decodeHexDump(hex);
        Packet packet = Packet.read(Unpooled.wrappedBuffer(bytes));
        packet.vin = args[0].toString();
        return packet;
    }

    public ByteBuf toByteBuf(Packet sample, long ts) {
        ByteBuf buffer = Unpooled.buffer();
        ((VehicleRunData) sample.data).collectTime = new Date(ts);
        sample.write(buffer);
        return buffer;
    }

    @Override
    public boolean ws_onSampleUpdate() {
        ByteBuf buffer = Unpooled.buffer();
        sample.write(buffer);
        int actualLen = buffer.readableBytes() - 25;
        int exceptLen = sample.contentLength;
        if (exceptLen == actualLen) {
            return false;
        } else {
            sample.contentLength = actualLen;
            return true;
        }
    }
}
