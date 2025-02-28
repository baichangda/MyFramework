package cn.bcd.lib.parser.protocol.gb32960.util;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
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
    public static byte[] buildBytes_timeData(String vin, Date time, PacketFlag flag, int replyFlag) {
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

    public static ByteBuf buildByteBuf_timeData(String vin, Date time, PacketFlag flag, int replyFlag) {
        return Unpooled.wrappedBuffer(buildBytes_timeData(vin, time, flag, replyFlag));
    }
}
