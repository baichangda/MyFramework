package cn.bcd.app.simulator.pressTest.tcp;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class WriteAccountingTest {
    @Test
    void countsOnlyCompletedSuccessfulWrites() throws Exception {
        verifyWrite(true);
    }

    @Test
    void failedWriteDoesNotIncreaseSendCount() throws Exception {
        verifyWrite(false);
    }

    private static void verifyWrite(boolean success) throws Exception {
        PressTest<?> pressTest = new PressTestLifecycleTest.TestPressTest();
        CountDownLatch written = new CountDownLatch(1);
        AtomicReference<ChannelPromise> pending = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                pending.set(promise);
                written.countDown();
            }
        }) {
            @Override
            public io.netty.channel.ChannelFuture writeAndFlush(Object msg) {
                // EmbeddedEventLoop treats every caller as an event loop thread.
                // A separate promise executor allows the sender to wait for completion.
                return super.writeAndFlush(msg, new DefaultChannelPromise(this,
                        io.netty.util.concurrent.GlobalEventExecutor.INSTANCE));
            }
        };
        Thread sender = Thread.ofVirtual().start(() -> {
            try {
                pressTest.send(channel, Unpooled.buffer(1).writeByte(42));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        try {
            assertTrue(written.await(3, TimeUnit.SECONDS));
            assertEquals(0, pressTest.sendNum.get());
            IOException expected = new IOException("write failed");
            if (success) {
                pending.get().setSuccess();
            } else {
                pending.get().setFailure(expected);
            }
            sender.join(3000);
            assertFalse(sender.isAlive());
            assertEquals(success ? 1 : 0, pressTest.sendNum.get());
            if (success) {
                assertNull(failure.get());
            } else {
                assertSame(expected, failure.get());
            }
        } finally {
            sender.interrupt();
            sender.join(3000);
            channel.finishAndReleaseAll();
        }
    }
}
