package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;

public class TerminalRegisterRequest implements PacketBody {
    //省域id
    public int provinceId;
    //市县域id
    public int cityId;
    //制造商id
    public byte[] manufacturerId;
    //终端型号
    public byte[] terminalType;
    //终端id
    public byte[] terminalId;
    //车牌颜色
    public short plateColor;
    //车牌
    public String plateNo;

    static final Charset gbk = Charset.forName("GBK");

    public static TerminalRegisterRequest read(ByteBuf data, int len) {
        TerminalRegisterRequest terminalRegisterRequest = new TerminalRegisterRequest();
        terminalRegisterRequest.provinceId = data.readUnsignedShort();
        terminalRegisterRequest.cityId = data.readUnsignedShort();
        byte[] bytes1 = new byte[11];
        data.readBytes(bytes1);
        terminalRegisterRequest.manufacturerId = bytes1;
        byte[] bytes2 = new byte[30];
        data.readBytes(bytes2);
        terminalRegisterRequest.terminalType = bytes2;
        byte[] bytes3 = new byte[30];
        data.readBytes(bytes3);
        terminalRegisterRequest.terminalId = bytes3;
        terminalRegisterRequest.plateColor = data.readUnsignedByte();
        terminalRegisterRequest.plateNo = data.readCharSequence(len - 76, gbk).toString();
        return terminalRegisterRequest;
    }

    public void write(ByteBuf data) {
        data.writeShort(provinceId);
        data.writeShort(cityId);
        data.writeBytes(manufacturerId);
        data.writeBytes(terminalType);
        data.writeBytes(terminalId);
        data.writeByte(plateColor);
        data.writeCharSequence(plateNo, gbk);
    }
}
