package cn.bcd.lib.parser.protocol.gb32960.util;

import cn.bcd.lib.base.util.DateUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.base.anno.data.NumVal_byte;
import cn.bcd.lib.parser.base.util.ParseUtil;
import cn.bcd.lib.parser.protocol.gb32960.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.data.PlatformLoginData;
import cn.bcd.lib.parser.protocol.gb32960.data.PlatformLogoutData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.time.LocalDateTime;
import java.util.Date;

public class PacketUtil {


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

    /**
     * 获取报文时间
     *
     * @param data
     * @return
     */
    public static Date getTime(ByteBuf data) {
        return Date.from(LocalDateTime.of(data.getByte(24) + 2000,
                data.getByte(25),
                data.getByte(26),
                data.getByte(27),
                data.getByte(28),
                data.getByte(29)).toInstant(DateZoneUtil.ZONE_OFFSET));
    }

    /**
     * 获取报文时间
     *
     * @param bytes
     * @return
     */
    public static Date getTime(byte[] bytes) {
        return Date.from(LocalDateTime.of(bytes[24] + 2000,
                bytes[25],
                bytes[26],
                bytes[27],
                bytes[28],
                bytes[29]).toInstant(DateZoneUtil.ZONE_OFFSET));
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
    public static byte[] build_bytes_timeData(String vin, Date time, PacketFlag flag, int replyFlag) {
        byte[] bytes = new byte[30];
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

    /**
     * 构造内容只包含时间的通用应答
     *
     * @param vin
     * @param time
     * @param flag
     * @param replyFlag
     * @return
     */
    public static ByteBuf build_byteBuf_timeData(String vin, Date time, PacketFlag flag, int replyFlag) {
        return Unpooled.wrappedBuffer(build_bytes_timeData(vin, DateUtil.clearMills(time), flag, replyFlag));
    }

    /**
     * 构造平台登录报文
     * @param vin
     * @param time
     * @param sn
     * @param username
     * @param password
     * @return
     */
    public static Packet build_packet_command_platformLogin(String vin, Date time, int sn, String username, String password) {
        PlatformLoginData platformLoginData = new PlatformLoginData();
        platformLoginData.collectTime = DateUtil.clearMills(time);
        platformLoginData.sn = sn;
        platformLoginData.username = username;
        platformLoginData.password = password;
        platformLoginData.encode = new NumVal_byte(0, (byte) 1);
        Packet packet = new Packet();
        packet.header = new byte[]{0x23, 0x23};
        packet.flag = PacketFlag.platform_login_data;
        packet.replyFlag = 0xFE;
        packet.vin = vin;
        packet.encodeWay = new NumVal_byte(0, (byte) 1);
        packet.contentLength = 41;
        packet.data = platformLoginData;
        return packet;
    }

    /**
     * 构造平台登出报文
     * @param vin
     * @param time
     * @param sn
     * @return
     */
    public static Packet build_packet_command_platformLogout(String vin, Date time, int sn) {
        PlatformLogoutData platformLogoutData = new PlatformLogoutData();
        platformLogoutData.collectTime = DateUtil.clearMills(time);
        platformLogoutData.sn = sn;
        Packet packet = new Packet();
        packet.header = new byte[]{0x23, 0x23};
        packet.flag = PacketFlag.platform_logout_data;
        packet.replyFlag = 0xFE;
        packet.vin = vin;
        packet.encodeWay = new NumVal_byte(0, (byte) 1);
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
