package cn.bcd.lib.parser.base; 
import cn.bcd.lib.parser.base.anno.*; 
import cn.bcd.lib.parser.base.data.NumType; 
import cn.bcd.lib.parser.base.processor.*; 
import io.netty.buffer.*; 
import org.junit.jupiter.api.Test; 
import static org.junit.jupiter.api.Assertions.*; 
public class ReviewProbeTest { 
    @Test public void uint64ArrayBuilds() { Parser.getProcessor(U64.class); } 
    public static class U64 { 
        @F_num_array(singleType=NumType.uint64,len=1) public long[] values; 
    } 
    @Test public void globalVariableCanBeUsedByTwoStrings() { Parser.getProcessor(TwoStrings.class); } 
    public static class TwoStrings { 
        @F_num(type=NumType.uint8,globalVar='A') public int len; 
        @F_string(lenExpr="A") public String first; 
        @F_string(lenExpr="A") public String second; 
    } 
    @Test public void globalVariableUpdateIsVisible() { 
        Processor<Updated> p=Parser.getProcessor(Updated.class); 
        ByteBuf b=Unpooled.wrappedBuffer(new byte[]{1,10,2,20,21}); 
        try { Updated v=p.process(b); assertEquals(2,v.second.length); assertEquals(5,b.readerIndex()); } 
        finally { b.release(); } 
    } 
    public static class Updated { 
        @F_num(type=NumType.uint8,globalVar='A') public int len; 
        @F_num_array(singleType=NumType.uint8,lenExpr="A") public byte[] first; 
        @F_num(type=NumType.uint8,globalVar='A') public int len2; 
        @F_num_array(singleType=NumType.uint8,lenExpr="A") public byte[] second; 
    } 
    @Test public void emptyBitArrayPreservesPadding() { 
        Processor<Bits> p=Parser.getProcessor(Bits.class); 
        ByteBuf b=Unpooled.wrappedBuffer(new byte[]{0,0,7}); 
        ByteBuf out=Unpooled.buffer(); 
        try { Bits v=p.process(b); p.deProcess(out,v); assertEquals(3,out.writerIndex()); } 
        finally { b.release(); out.release(); } 
    } 
    public static class Bits { 
        @F_num(type=NumType.uint8,var='a') public int count; 
        @F_bit_num_array(singleLen=1,lenExpr="a",skipAfter=8) public int[] values; 
        @F_num(type=NumType.uint8) public int tail; 
    } 
    @Test public void interfaceClassPaddingUsesActualSize() { 
        Processor<Envelope> p=Parser.getProcessor(Envelope.class); 
        Envelope e=new Envelope(); e.item=new ItemImpl(); 
        ByteBuf out=Unpooled.buffer(); 
        try { p.deProcess(out,e); assertEquals(4,out.writerIndex()); } 
        finally { out.release(); } 
    } 
    public interface Item {} 
    @C_impl(value=1) public static class ItemImpl implements Item { 
        @F_num(type=NumType.uint8) public int value; 
    } 
    @C_skip(len=4) public static class Envelope { 
        @F_bean(implClassExpr="1") public Item item; 
    } 
}
