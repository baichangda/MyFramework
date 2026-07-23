package cn.bcd.lib.parser.protocol.gb32960.v2016;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput})
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class PacketReadBenchmark {
    private ByteBuf packetBuffer;

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(PacketReadBenchmark.class.getName())
                .build();
        new Runner(options).run();
    }

    @Setup
    public void setup() {
        byte[] packetBytes = ByteBufUtil.decodeHexDump(Const.sample_vehicleRunData);
        packetBuffer = Unpooled.wrappedBuffer(packetBytes);
    }

    @Benchmark
    public Packet read() {
        packetBuffer.readerIndex(0);
        return Packet.read(packetBuffer);
    }
}
