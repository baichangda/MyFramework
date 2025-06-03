package cn.bcd.lib.parser.protocol.jtt808.v2019.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketUtil {
    /**
     * 转义处理
     * 0x7d -> 0x7d01
     * 0x7e -> 0x7d02
     *
     * @param data
     * @return
     */
    public static ByteBuf escape(ByteBuf data) {
        ByteBuf res = Unpooled.buffer(data.readableBytes());
        data.forEachByte(b -> {
            switch (b & 0xff) {
                case 0x7d -> res.writeBytes(new byte[]{0x7d, 0x01});
                case 0x7e -> res.writeBytes(new byte[]{0x7d, 0x02});
                default -> res.writeByte(b);
            }
            return true;
        });
        return res;
    }

    public static ByteBuf escape(byte[] data) {
        ByteBuf res = Unpooled.buffer(data.length);
        for (byte b : data) {
            switch (b & 0xff) {
                case 0x7d -> res.writeBytes(new byte[]{0x7d, 0x01});
                case 0x7e -> res.writeBytes(new byte[]{0x7d, 0x02});
                default -> res.writeByte(b);
            }
        }
        return res;
    }

    /**
     * 逆转义处理
     * 0x7d01 -> 0x7d
     * 0x7d02 -> 0x7e
     *
     * @param data
     * @return
     */
    public static ByteBuf unEscape(byte[] data) {
        ByteBuf res = Unpooled.buffer(data.length);
        boolean escape = false;
        for (byte b : data) {
            if (escape) {
                if ((b & 0xff) == 1) {
                    res.writeByte(0x7d);
                } else {
                    res.writeByte(0x7e);
                }
                escape = false;
            } else {
                if ((b & 0xff) == 0x7d) {
                    escape = true;
                } else {
                    res.writeByte(b);
                }
            }
        }
        return res;
    }

    public static ByteBuf unEscape(ByteBuf data) {
        ByteBuf res = Unpooled.buffer(data.readableBytes());
        boolean[] escape = new boolean[1];
        data.forEachByte(b -> {
            if (escape[0]) {
                if ((b & 0xff) == 1) {
                    res.writeByte(0x7d);
                } else {
                    res.writeByte(0x7e);
                }
                escape[0] = false;
            } else {
                if ((b & 0xff) == 0x7d) {
                    escape[0] = true;
                } else {
                    res.writeByte(b);
                }
            }
            return true;
        });
        return res;
    }
}
