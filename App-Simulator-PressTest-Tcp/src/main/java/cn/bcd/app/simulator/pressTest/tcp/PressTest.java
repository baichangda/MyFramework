package cn.bcd.app.simulator.pressTest.tcp;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class PressTest<T> implements Runnable {
    final Logger logger = LoggerFactory.getLogger(this.getClass());
    AtomicInteger clientNum = new AtomicInteger();
    AtomicInteger sendNum = new AtomicInteger();

    @CommandLine.ParentCommand
    Starter starter;

    /**
     * 根据vin初始化报文bean
     *
     * @param vin
     * @return
     */
    protected abstract T initSample(String vin);

    /**
     * 发送之前、得到{@link ByteBuf}
     *
     * @param t  {@link #initSample(String)}得到的报文bean
     * @param ts 本次发送报文时间戳
     * @return
     */
    protected abstract ByteBuf toByteBuf(T t, long ts);

    @Override
    public void run() {
        HostAndPort address = starter.validate();
        AtomicBoolean running = new AtomicBoolean(true);
        MultiThreadIoEventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        ChannelGroup channels = new DefaultChannelGroup(eventLoopGroup.next(), true);
        try (ScheduledExecutorService statistics = Executors.newSingleThreadScheduledExecutor();
             ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(eventLoopGroup);
                bootstrap.channel(NioSocketChannel.class);
                bootstrap.option(ChannelOption.TCP_NODELAY, true);
                bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        channels.add(ch);
                        ch.closeFuture().addListener(future -> running.set(false));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
                    }
                });
                statistics.scheduleAtFixedRate(() -> {
                    logger.info("client[{}] sendSpeed[{}]/s", clientNum.get(), sendNum.getAndSet(0) / 3);
                }, 3, 3, TimeUnit.SECONDS);
                String[] vins = getVins();
                int period = starter.period;
                if (vins.length < period) {
                    for (String vin : vins) {
                        startClient(vin, running, clients, bootstrap, address);
                    }
                } else {
                    int batchNum = vins.length / period;
                    for (int i = 0; i < period && running.get(); i++) {
                        int end = i == period - 1 ? vins.length : (i + 1) * batchNum;
                        for (int j = i * batchNum; j < end; j++) {
                            startClient(vins[j], running, clients, bootstrap, address);
                        }
                        TimeUnit.SECONDS.sleep(1);
                    }
                }
                while (running.get()) {
                    TimeUnit.SECONDS.sleep(3);
                }
            } finally {
                running.set(false);
                clients.shutdownNow();
                channels.close().awaitUninterruptibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BaseException.get(ex);
        } catch (Exception ex) {
            throw BaseException.get(ex);
        } finally {
            eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private void startClient(String vin, AtomicBoolean running, ExecutorService clients,
                             Bootstrap bootstrap, HostAndPort address) {
        if (!running.get()) {
            return;
        }
        clients.submit(() -> {
            Channel channel = null;
            boolean added = false;
            try {
                if (!running.get()) {
                    return;
                }
                T sample = initSample(vin);
                ChannelFuture connectFuture = bootstrap.connect(address.getHost(), address.getPort());
                channel = connectFuture.channel();
                connectFuture.sync();
                if (!running.get()) {
                    return;
                }
                clientNum.incrementAndGet();
                added = true;
                long sendTs = DateUtil.CacheMillisecond.current();
                while (running.get() && channel.isActive()) {
                    long waitMillis = sendTs - DateUtil.CacheMillisecond.current();
                    if (waitMillis > 0) {
                        TimeUnit.MILLISECONDS.sleep(waitMillis);
                    }
                    if (!running.get() || !channel.isActive()) {
                        return;
                    }
                    ByteBuf buffer = toByteBuf(sample, sendTs);
                    logger.debug("client[{}] vin[{}] waitMillis[{}]", clientNum.get(), vin, waitMillis);
                    // Wait on the virtual thread so failed or pending writes are not counted.
                    send(channel, buffer);
                    sendTs += starter.period * 1000L;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (Exception ex) {
                logger.error("client vin[{}] failed", vin, ex);
                running.set(false);
            } finally {
                if (channel != null) {
                    channel.close();
                }
                if (added) {
                    clientNum.decrementAndGet();
                }
            }
        });
    }

    void send(Channel channel, ByteBuf buffer) throws InterruptedException {
        channel.writeAndFlush(buffer).sync();
        sendNum.incrementAndGet();
    }

    public String[] getVins() {
        String vinPrefix = "TEST000000";
        int num = starter.num;
        int startIndex = starter.startIndex;
        String[] vins = new String[num];
        for (int i = 0; i < num; i++) {
            vins[i] = vinPrefix + Strings.padStart(Integer.toString(startIndex + i), 7, '0');
        }
        return vins;
    }
}
