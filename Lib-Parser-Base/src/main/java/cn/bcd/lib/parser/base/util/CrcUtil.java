package cn.bcd.lib.parser.base.util;

import io.netty.buffer.ByteBuf;

public class CrcUtil {
    // MPEG-2 CRC32 多项式：0x04C11DB7（多项式的二进制为 10011000001000111011011010111）
    private static final long POLYNOMIAL = 0x04C11DB7L;
    // 预计算 CRC 表（加速计算）
    private static final long[] CRC_TABLE = new long[256];

    // 静态初始化 CRC 表
    static {
        for (int i = 0; i < 256; i++) {
            long crc = i;
            for (int j = 0; j < 8; j++) {
                // 每次左移 1 位，若最高位为 1 则与多项式异或
                if ((crc & 0x80000000L) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }
                // 保留低 32 位
                crc &= 0xFFFFFFFFL;
            }
            CRC_TABLE[i] = crc;
        }
    }

    public static long crc32_mpeg_2(ByteBuf byteBuf, int offset, int length) {
        long crc = 0xFFFFFFFFL; // 初始值
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            // 取当前字节（无符号）与 CRC 高 8 位异或，作为查表索引
            int index = ((int) (crc >> 24) ^ (byteBuf.getByte(i) & 0xFF)) & 0xFF;
            // 更新 CRC：低 24 位左移 8 位，与表中值异或
            crc = (crc << 8) ^ CRC_TABLE[index];
            // 保留低 32 位
            crc &= 0xFFFFFFFFL;
        }
        return crc; // 不反转，不异或输出
    }

    public static long crc32_mpeg_2(byte[] data, int offset, int length) {
        long crc = 0xFFFFFFFFL; // 初始值
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            // 取当前字节（无符号）与 CRC 高 8 位异或，作为查表索引
            int index = ((int) (crc >> 24) ^ (data[i] & 0xFF)) & 0xFF;
            // 更新 CRC：低 24 位左移 8 位，与表中值异或
            crc = (crc << 8) ^ CRC_TABLE[index];
            // 保留低 32 位
            crc &= 0xFFFFFFFFL;
        }
        return crc; // 不反转，不异或输出
    }
}
