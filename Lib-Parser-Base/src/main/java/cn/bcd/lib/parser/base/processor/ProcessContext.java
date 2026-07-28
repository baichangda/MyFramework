package cn.bcd.lib.parser.base.processor;

import cn.bcd.lib.parser.base.util.BitBuf_reader;
import cn.bcd.lib.parser.base.log.BitBuf_reader_log;
import cn.bcd.lib.parser.base.util.BitBuf_writer;
import cn.bcd.lib.parser.base.log.BitBuf_writer_log;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.HashMap;
import cn.bcd.lib.parser.base.anno.F_bit_num;
import cn.bcd.lib.parser.base.anno.F_bit_num_array;

public class ProcessContext {
    public final ByteBuf byteBuf;

    /**
     * 在解析过程中如果用到如下注解
     * {@link F_bit_num}
     * {@link F_bit_num_array}
     * 则会在解析过程中赋值、参考{@link #getBitBuf_reader()}、{@link #getBitBuf_writer()}
     */
    public BitBuf_reader bitBuf_reader;
    public BitBuf_writer bitBuf_writer;

    /**
     * 解析过程中的全局变量。
     */
    public HashMap<String, Object> globalVars;

    /**
     * 创建一个解析环境
     * 此解析环境是root环境、没有父环境
     *
     * @param byteBuf
     */
    public ProcessContext(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    public final BitBuf_reader getBitBuf_reader() {
        if (bitBuf_reader == null) {
            bitBuf_reader = new BitBuf_reader(byteBuf);
        }
        return bitBuf_reader;
    }

    public final BitBuf_writer getBitBuf_writer() {
        if (bitBuf_writer == null) {
            bitBuf_writer = new BitBuf_writer(byteBuf);
        }
        return bitBuf_writer;
    }

    public final BitBuf_reader_log getBitBuf_reader_log() {
        if (bitBuf_reader == null) {
            bitBuf_reader = new BitBuf_reader_log(byteBuf);
        }
        return (BitBuf_reader_log) bitBuf_reader;
    }

    public final BitBuf_writer_log getBitBuf_writer_log() {
        if (bitBuf_writer == null) {
            bitBuf_writer = new BitBuf_writer_log(byteBuf);
        }
        return (BitBuf_writer_log) bitBuf_writer;
    }

    public final void putGlobalVar(String name, Object value) {
        if (globalVars == null) {
            globalVars = new HashMap<>();
        }
        globalVars.put(name, value);
    }

    public final Object getGlobalVar(String name) {
        return globalVars.get(name);
    }

    /**
     * 获取数值类型的全局变量并转换为 {@code int}。
     *
     * @param name 全局变量名称
     * @return 全局变量的整数值
     */
    public final int getGlobalVarInt(String name) {
        Number number = (Number) getGlobalVar(name);
        return number.intValue();
    }
}
