package cn.bcd.app.simulator.pressTest.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(15)
class PressTestLifecycleTest {
    @Test
    void disconnectStopsClientsWithoutWaitingForNextReport() throws Exception {
        try (ServerSocket server = server()) {
            TestPressTest pressTest = configured(server);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread runner = start(pressTest, failure);
            try {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    assertEquals(42, socket.getInputStream().read());
                    long deadline = System.nanoTime() + 1_000_000_000L;
                    while (pressTest.sendNum.get() == 0 && System.nanoTime() < deadline) {
                        Thread.sleep(10);
                    }
                    assertEquals(1, pressTest.sendNum.get());
                }
                runner.join(8000);
                assertFalse(runner.isAlive());
                assertNull(failure.get());
                assertEquals(0, pressTest.clientNum.get());
                assertEquals(1, pressTest.encoded.get());
            } finally {
                runner.interrupt();
                runner.join(5000);
            }
        }
    }

    @Test
    void interruptClosesOpenConnectionAndStopsWorkers() throws Exception {
        try (ServerSocket server = server()) {
            TestPressTest pressTest = configured(server);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread runner = start(pressTest, failure);
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                assertEquals(42, socket.getInputStream().read());
                runner.interrupt();
                runner.join(8000);
                assertFalse(runner.isAlive());
                assertEquals(-1, socket.getInputStream().read());
                assertEquals(0, pressTest.clientNum.get());
            } finally {
                runner.interrupt();
                runner.join(5000);
            }
        }
    }

    @Test
    void sampleInitializationFailureStopsRun() throws Exception {
        TestPressTest pressTest = new TestPressTest() {
            @Override
            protected String initSample(String vin) {
                throw new IllegalStateException("sample failed");
            }
        };
        pressTest.starter = StarterTest.validStarter();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread runner = start(pressTest, failure);
        try {
            runner.join(8000);
            assertFalse(runner.isAlive());
            assertNull(failure.get());
            assertEquals(0, pressTest.clientNum.get());
            assertEquals(0, pressTest.sendNum.get());
        } finally {
            runner.interrupt();
            runner.join(5000);
        }
    }

    private static ServerSocket server() throws Exception {
        ServerSocket server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(5000);
        return server;
    }

    private static TestPressTest configured(ServerSocket server) {
        TestPressTest pressTest = new TestPressTest();
        pressTest.starter = StarterTest.validStarter();
        pressTest.starter.tcpServerAddress = "127.0.0.1:" + server.getLocalPort();
        return pressTest;
    }

    private static Thread start(TestPressTest pressTest, AtomicReference<Throwable> failure) {
        return Thread.ofPlatform().start(() -> {
            try {
                pressTest.run();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
    }

    static class TestPressTest extends PressTest<String> {
        final AtomicInteger encoded = new AtomicInteger();

        @Override
        protected String initSample(String vin) {
            return vin;
        }

        @Override
        protected ByteBuf toByteBuf(String sample, long ts) {
            encoded.incrementAndGet();
            return Unpooled.buffer(1).writeByte(42);
        }
    }
}
