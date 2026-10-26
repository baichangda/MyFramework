package cn.bcd.app.simulator.singleVehicle.tcp;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.DefaultEventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class VehicleCallbackTest {
    @Test
    void connectedEventWaitsUntilChannelAssignment() throws Exception {
        DefaultEventExecutor executor = new DefaultEventExecutor();
        RecordingListener listener = new RecordingListener();
        Vehicle vehicle = new Vehicle("LSJE36096MS140495", 10, null, executor, listener);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            executor.submit(() -> {
                vehicle.onConnected(channel);
                assignChannel(vehicle, channel);
            }).sync();
            executor.submit(() -> {}).sync();
            assertEquals(1, listener.connected);
        } finally {
            executor.submit(vehicle::destroy).sync();
            channel.finishAndReleaseAll();
            executor.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    void callbacksFromOldConnectionCannotAffectReplacement() throws Exception {
        DefaultEventExecutor executor = new DefaultEventExecutor();
        RecordingListener listener = new RecordingListener();
        Vehicle vehicle = new Vehicle("LSJE36096MS140495", 10, null, executor, listener);
        EmbeddedChannel oldChannel = new EmbeddedChannel();
        EmbeddedChannel newChannel = new EmbeddedChannel();
        try {
            executor.submit(() -> {
                assignChannel(vehicle, oldChannel);
                vehicle.onConnected(oldChannel);
                vehicle.onDisconnected(oldChannel);
                vehicle.onMessage(oldChannel, new byte[]{1});
                assignChannel(vehicle, newChannel);
            }).sync();
            executor.submit(() -> {}).sync();
            assertEquals(0, listener.connected);
            assertEquals(0, listener.disconnected);
            assertEquals(0, listener.received);
            assertSame(newChannel, WsSessionLifecycleTest.field(vehicle, "channel", Channel.class));
        } finally {
            executor.submit(vehicle::destroy).sync();
            oldChannel.finishAndReleaseAll();
            newChannel.finishAndReleaseAll();
            executor.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    private static void assignChannel(Vehicle vehicle, Channel channel) {
        try {
            Field field = Vehicle.class.getDeclaredField("channel");
            field.setAccessible(true);
            field.set(vehicle, channel);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static class RecordingListener implements Vehicle.Listener {
        int connected;
        int disconnected;
        int received;

        @Override
        public void onTcpConnected() {
            connected++;
        }

        @Override
        public void onTcpConnectFailed(Throwable cause) {
        }

        @Override
        public void onTcpDisconnected() {
            disconnected++;
        }

        @Override
        public void onTcpSend(byte[] data) {
        }

        @Override
        public void onTcpReceive(byte[] data) {
            received++;
        }
    }
}
