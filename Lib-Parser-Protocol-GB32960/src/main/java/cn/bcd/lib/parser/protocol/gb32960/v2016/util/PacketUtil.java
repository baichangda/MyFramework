package cn.bcd.lib.parser.protocol.gb32960.v2016.util;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.base.util.ParseUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

public class PacketUtil {


    /**
     * 修正数据单元长度
     *
     * @param data 只包含一条数据的数据包
     */
    public static void fix_contentLength(byte[] data) {
        int actualLen = data.length - 25;
        data[22] = (byte) (actualLen >> 8);
        data[23] = (byte) actualLen;
    }

    /**
     * 修正数据单元长度
     *
     * @param data 只包含一条数据的数据包
     */
    public static void fix_contentLength(ByteBuf data) {
        int actualLen = data.readableBytes() - 25;
        data.setShort(22, actualLen);
    }

    /**
     * 修正异或校验位
     *
     * @param data 只包含一条数据的数据包
     */
    public static void fix_code(ByteBuf data) {
        byte xor = 0;
        int codeIndex = data.readableBytes() - 1;
        for (int i = 0; i < codeIndex; i++) {
            xor ^= data.getByte(i);
        }
        data.setByte(codeIndex, xor);
    }

    /**
     * 修正异或校验位
     *
     * @param data 只包含一条数据的数据包
     */
    public static void fix_code(byte[] data) {
        byte xor = 0;
        int codeIndex = data.length - 1;
        for (int i = 0; i < codeIndex; i++) {
            xor ^= data[i];
        }
        data[codeIndex] = xor;
    }


    public static PacketFlag getPacketFlag(ByteBuf byteBuf) {
        return getPacketFlag(byteBuf, 0);
    }

    public static PacketFlag getPacketFlag(ByteBuf byteBuf, int offset) {
        return PacketFlag.fromInteger(byteBuf.getUnsignedByte(offset + 2));
    }

    public static PacketFlag getPacketFlag(byte[] bytes) {
        return getPacketFlag(bytes, 0);
    }

    public static PacketFlag getPacketFlag(byte[] bytes, int offset) {
        return PacketFlag.fromInteger(bytes[offset + 2] & 0xFF);
    }

    public static int getReplyFlag(ByteBuf byteBuf) {
        return getReplyFlag(byteBuf, 0);
    }

    public static int getReplyFlag(ByteBuf byteBuf, int offset) {
        return byteBuf.getUnsignedByte(offset + 3);
    }

    public static int getReplyFlag(byte[] bytes) {
        return getReplyFlag(bytes, 0);
    }

    public static int getReplyFlag(byte[] bytes, int offset) {
        return bytes[offset + 3] & 0xFF;
    }


    public static String getVin(byte[] bytes) {
        return getVin(bytes, 0);
    }

    public static String getVin(byte[] bytes, int offset) {
        return new String(bytes, offset + 4, 17);
    }

    public static String getVin(ByteBuf byteBuf) {
        return getVin(byteBuf, 0);
    }

    public static String getVin(ByteBuf byteBuf, int offset) {
        return byteBuf.getCharSequence(offset + 4, 17, StandardCharsets.UTF_8).toString();
    }

    public static Date getTime(ByteBuf data) {
        return getTime(data, 0);
    }

    public static Date getTime(ByteBuf data, int offset) {
        return Date.from(LocalDateTime.of(data.getByte(offset + 24) + 2000,
                data.getByte(offset + 25),
                data.getByte(offset + 26),
                data.getByte(offset + 27),
                data.getByte(offset + 28),
                data.getByte(offset + 29)).toInstant(DateZoneUtil.ZONE_OFFSET));
    }


    public static Date getTime(byte[] bytes) {
        return getTime(bytes, 0);
    }

    public static Date getTime(byte[] bytes, int offset) {
        return Date.from(LocalDateTime.of(bytes[offset + 24] + 2000,
                bytes[offset + 25],
                bytes[offset + 26],
                bytes[offset + 27],
                bytes[offset + 28],
                bytes[offset + 29]).toInstant(DateZoneUtil.ZONE_OFFSET));
    }

    public static byte[] getPacketData_bytes(byte[] bytes) {
        return getPacketData_bytes(bytes, 0);
    }

    public static byte[] getPacketData_bytes(byte[] bytes, int offset) {
        byte[] content = new byte[bytes.length - 25 - offset];
        System.arraycopy(bytes, 24 + offset, content, 0, content.length);
        return content;
    }

    public static byte[] getPacketData_bytes(ByteBuf byteBuf) {
        return getPacketData_bytes(byteBuf, 0);
    }

    public static byte[] getPacketData_bytes(ByteBuf byteBuf, int offset) {
        byte[] content = new byte[byteBuf.readableBytes() - 25 - offset];
        byteBuf.getBytes(24 + offset, content, 0, content.length);
        return content;
    }

    /**
     * 构造内容只包含时间的通用应答
     *
     * @param vin
     * @param time
     * @param flag
     * @param replyFlag
     * @return
     */
    public static byte[] build_bytes_timeData(String vin, PacketFlag flag, int replyFlag, Date time) {
        byte[] bytes = new byte[31];
        bytes[0] = 0x23;
        bytes[1] = 0x23;
        bytes[2] = (byte) flag.type;
        bytes[3] = (byte) replyFlag;
        System.arraycopy(vin.getBytes(), 0, bytes, 4, 17);
        bytes[21] = 1;
        bytes[22] = 0;
        bytes[23] = 6;
        LocalDateTime ldt = LocalDateTime.ofInstant(time.toInstant(), DateZoneUtil.ZONE_ID);
        bytes[24] = (byte) (ldt.getYear() - 2000);
        bytes[25] = (byte) ldt.getMonth().getValue();
        bytes[26] = (byte) ldt.getDayOfMonth();
        bytes[27] = (byte) ldt.getHour();
        bytes[28] = (byte) ldt.getMinute();
        bytes[29] = (byte) ldt.getSecond();
        fix_code(bytes);
        return bytes;
    }

    public static ByteBuf build_byteBuf_timeData(String vin, PacketFlag flag, int replyFlag, Date time) {
        return Unpooled.wrappedBuffer(build_bytes_timeData(vin, flag, replyFlag, DateUtil.clearMills(time)));
    }

    /**
     * 根据报文体内容构造报文
     *
     * @param vin
     * @param flag
     * @param replyFlag
     * @param content
     * @return
     */
    public static byte[] build_bytes_packetData(String vin, PacketFlag flag, int replyFlag, byte[] content) {
        int length = content.length;
        byte[] bytes = new byte[length + 25];
        bytes[0] = 0x23;
        bytes[1] = 0x23;
        bytes[2] = (byte) flag.toInteger();
        bytes[3] = (byte) replyFlag;
        System.arraycopy(vin.getBytes(), 0, bytes, 4, 17);
        bytes[21] = 1;
        bytes[22] = (byte) (length >> 8);
        bytes[23] = (byte) length;
        System.arraycopy(content, 0, bytes, 24, length);
        fix_code(bytes);
        return bytes;
    }

    public static ByteBuf build_byteBuf_packetData(String vin, PacketFlag flag, int replyFlag, byte[] content) {
        return Unpooled.wrappedBuffer(build_bytes_packetData(vin, flag, replyFlag, content));
    }

    public static byte[] build_bytes_packetData(String vin, PacketFlag flag, short replyFlag, PacketData packetData) {
        ByteBuf buffer = build_byteBuf_packetData(vin, flag, replyFlag, packetData);
        byte[] arr = new byte[buffer.readableBytes()];
        buffer.readBytes(arr);
        return arr;
    }

    public static ByteBuf build_byteBuf_packetData(String vin, PacketFlag flag, short replyFlag, PacketData packetData) {
        Packet packet = new Packet();
        packet.header = new byte[]{0x23, 0x23};
        packet.flag = flag;
        packet.replyFlag = replyFlag;
        packet.vin = vin;
        packet.encodeWay = 1;
        packet.code = 0;
        packet.data = packetData;
        return packet.toByteBuf_fixAll();
    }


    public static byte[] build_bytes_common_response(byte[] data, byte replyFlag) {
        byte[] response = new byte[31];
        System.arraycopy(data, 0, response, 0, 30);
        response[3] = replyFlag;
        response[22] = 0;
        response[23] = 6;
        fix_code(response);
        return response;
    }

    /**
     * 构造平台登录报文
     *
     * @param vin
     * @param time
     * @param sn
     * @param username
     * @param password
     * @return
     */
    public static Packet build_packet_command_platformLogin(String vin, Date time, int sn, String username, String password) {
        if (!(sn >= 1 && sn <= 65531)) {
            throw BaseException.get("sn must be between 1 and 65531,now is {}", sn);
        }
        if (username.length() != 12) {
            throw BaseException.get("username length must be 12,now is {}", username.length());
        }
        if (password.length() != 20) {
            throw BaseException.get("password length must be 20,now is {}", password.length());
        }
        PlatformLoginData platformLoginData = new PlatformLoginData();
        platformLoginData.collectTime = DateUtil.clearMills(time);
        platformLoginData.sn = sn;
        platformLoginData.username = username;
        platformLoginData.password = password;
        platformLoginData.encode = 1;
        Packet packet = new Packet();
        packet.header = new byte[]{0x23, 0x23};
        packet.flag = PacketFlag.platform_login_data;
        packet.replyFlag = 0xFE;
        packet.vin = vin;
        packet.encodeWay = 1;
        packet.contentLength = 41;
        packet.data = platformLoginData;
        return packet;
    }

    /**
     * 构造平台登出报文
     *
     * @param vin
     * @param time
     * @param sn
     * @return
     */
    public static Packet build_packet_command_platformLogout(String vin, Date time, int sn) {
        if (!(sn >= 1 && sn <= 65531)) {
            throw BaseException.get("sn must be between 1 and 65531,now is {}", sn);
        }
        PlatformLogoutData platformLogoutData = new PlatformLogoutData();
        platformLogoutData.collectTime = DateUtil.clearMills(time);
        platformLogoutData.sn = sn;
        Packet packet = new Packet();
        packet.header = new byte[]{0x23, 0x23};
        packet.flag = PacketFlag.platform_logout_data;
        packet.replyFlag = 0xFE;
        packet.vin = vin;
        packet.encodeWay = 1;
        packet.contentLength = 8;
        packet.data = platformLogoutData;
        return packet;
    }


    public static void main(String[] args) {
        //contentLength
        System.out.println(ParseUtil.getClassByteLenIfPossible(PlatformLoginData.class));
        System.out.println(ParseUtil.getClassByteLenIfPossible(PlatformLogoutData.class));
    }
}
