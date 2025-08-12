package cn.bcd.app.dataProcess.transfer.v2016.tcp;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.executor.queue.MpscArrayBlockingQueue;
import cn.bcd.lib.base.executor.queue.WaitStrategy;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.data.init.transferConfig.TransferConfigData;
import cn.bcd.lib.data.notify.onlyNotify.platformStatus.PlatformStatusData;
import cn.bcd.lib.data.notify.onlyNotify.platformStatus.PlatformStatusSender;
import cn.bcd.lib.parser.base.util.ParseUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PlatformLoginData;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import cn.bcd.app.dataProcess.transfer.v2016.DataConsumer;
import cn.bcd.app.dataProcess.transfer.v2016.handler.TransferDataHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public class TcpClient {
    static Logger logger = LoggerFactory.getLogger(TcpClient.class);

    static final String REDIS_KEY_PRE_PLATFORM_SN = "platformSn:";
    static final String REDIS_KEY_PRE_PLATFORM_STATUS = "platformStatus:";
    static final String REDIS_KEY_PRE_PLATFORM_LAST_HEARTBEAT_TIME = "platformLastHeartbeatTime:";
    static final String REDIS_KEY_PRE_PLATFORM_LAST_LOGIN_TIME = "platformLastLoginTime:";
    static final String REDIS_KEY_PRE_PLATFORM_LAST_LOGIN_SN = "platformLastLoginSn:";

    public static TransferConfigData transferConfigData;
    public static DataConsumer dataConsumer;
    static RedisTemplate<String, String> redisTemplate;
    static PlatformStatusSender platformStatusSender;
    static Bootstrap bootstrap;
    static Channel channel;
    static ScheduledExecutorService manageExecutor;

    public final static int SEND_QUEUE_SIZE = 10000;
    public final static MpscArrayBlockingQueue<SendData> sendQueue = new MpscArrayBlockingQueue<>(SEND_QUEUE_SIZE, WaitStrategy.PROGRESSIVE_10MS);
    static ExecutorService sendExecutor;

    static int platformLoginSn;
    static Date platformLoginPacketTime;
    static ScheduledFuture<?> heartbeatFuture;

    /**
     * 平台是否已登录
     */
    static boolean isLogin = false;

    public static LongAdder sendNum = new LongAdder();

    /**
     * 连接重试次数
     */
    static int connectRetryCount = 0;

    /**
     * 是否在断开连接后自动重连
     */
    static boolean autoConnectOnDisconnect = true;


    public static CompletableFuture<Void> init(TransferConfigData transferConfigData,
                                               DataConsumer dataConsumer,
                                               RedisTemplate<String, String> redisTemplate,
                                               PlatformStatusSender platformStatusSender
    ) {
        synchronized (TcpClient.class) {
            if (TcpClient.manageExecutor == null) {
                TcpClient.manageExecutor = Executors.newSingleThreadScheduledExecutor();
            }
        }
        return execute(() -> {
            //初始化数据
            TcpClient.transferConfigData = transferConfigData;
            TcpClient.dataConsumer = dataConsumer;
            TcpClient.redisTemplate = redisTemplate;
            TcpClient.platformStatusSender = platformStatusSender;
            TcpClient.bootstrap = new Bootstrap();
            TcpClient.bootstrap.group(new NioEventLoopGroup());
            TcpClient.bootstrap.channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true);
            TcpClient.bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(@NotNull SocketChannel ch) {
                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(10 * 1024, 22, 2, 1, 0));
                    ch.pipeline().addLast(new TcpClientHandler());
                }
            });
            //启动管理线程
            startScheduledExecutor(transferConfigData.serverId);
            //启动发送线程
            startSendExecutor();
            //连接
            connect();
        });
    }

    /**
     * 平台登陆
     *
     * @return 结果码
     * 0、平台登陆成功
     * 1、平台已处于登陆状态
     * 2、平台登陆失败、原因是程序异常
     */
    public static CompletableFuture<Integer> platformLogin() {
        return execute(() -> {
            if (isLogin) {
                return 1;
            } else {
                //启用断线重连
                autoConnectOnDisconnect = true;
                //连接
                connect();
                return 0;
            }
        });
    }

    /**
     * 平台登出
     *
     * @return 结果码
     * 0、平台登出成功
     * 1、平台已处于登出状态
     * 2、平台登出失败、原因是程序异常
     */
    public static CompletableFuture<Integer> platformLogout() {
        return execute(() -> {
            if (isLogin) {
                //禁用断线重连
                autoConnectOnDisconnect = false;
                //发送平台登出
                boolean b = platformLogoutInternal();
                return b ? 0 : 2;
            } else {
                return 1;
            }
        });
    }

    public static void onConnectRes(ChannelFuture future) {
        execute(() -> {
            if (future.isSuccess()) {
                logger.info("connect succeed host[{}] port[{}]", transferConfigData.upAddress, transferConfigData.upPort);
                //设置channel
                TcpClient.channel = future.channel();
                //重置重连次数
                connectRetryCount = 0;
                //进行平台登陆
                platformLoginInternal();
            } else {
                Throwable throwable = future.exceptionNow();
                logger.error("connect failed host[{}] port[{}]", transferConfigData.upAddress, transferConfigData.upPort, throwable);
                //进行重连
                //重连次数累加
                connectRetryCount++;
                if (connectRetryCount < 3) {
                    logger.info("connect failed、connectRetryCount[{}]、retry after 1m", connectRetryCount);
                    manageExecutor.schedule(TcpClient::connect, 1, TimeUnit.MINUTES);
                } else {
                    logger.info("connect failed、connectRetryCount[{}]、retry after 30m", connectRetryCount);
                    manageExecutor.schedule(TcpClient::connect, 30, TimeUnit.MINUTES);
                }
            }

        });
    }

    public static void onDisconnect() {
        execute(() -> {
            logger.info("disconnected host[{}] port[{}]", transferConfigData.upAddress, transferConfigData.upPort);
            //设置不可用
            setIsLogin(false);
            //暂停消费
            dataConsumer.pauseConsume();
            //关闭心跳发送
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
            //发送掉线通知
            PlatformStatusData platformStatusData = new PlatformStatusData();
            platformStatusData.setStatus(0);
            platformStatusData.setServerId(transferConfigData.serverId);
            platformStatusData.setTime(new Date());
            platformStatusSender.send(platformStatusData);
            //重新连接
            if (autoConnectOnDisconnect) {
                connect();
            }
        });
    }

    public static void onMessage(ByteBuf byteBuf) {
        PacketFlag flag = PacketFlag.fromInteger(byteBuf.getByte(2));
        if (Const.logEnable) {
            logger.info("--------------------------receive type[{}]:\n{}", flag, ByteBufUtil.hexDump(byteBuf));
        }
        switch (flag) {
            case heartbeat -> execute(() -> onHeartbeatResponse(byteBuf));
            case platform_login_data -> execute(() -> onPlatformLoginResponse(byteBuf));
            case platform_logout_data -> execute(() -> onPlatformLogoutResponse(byteBuf));
            default -> {
                String vin = byteBuf.getCharSequence(4, 17, StandardCharsets.UTF_8).toString();
                TransferDataHandler handler = dataConsumer.getHandler(vin);
                if (handler == null) {
                    logger.warn("vin[{}] not exist when on tcp message:\n{}", vin, ByteBufUtil.hexDump(byteBuf));
                    return;
                }
                handler.executor.execute(() -> {
                    try {
                        handler.onTcpMessage(byteBuf);
                    } catch (Exception ex) {
                        logger.error("error", ex);
                    }
                });
            }
        }
    }

    private static void connect() {
        try {
            String host = TcpClient.transferConfigData.upAddress;
            int port = TcpClient.transferConfigData.upPort;
            //连接tcp server
            bootstrap.connect(host, port).addListener((ChannelFutureListener) TcpClient::onConnectRes);
        } catch (Exception ex) {
            logger.error("error", ex);
        }
    }

    private static void onPlatformLoginResponse(ByteBuf byteBuf) {
        Date time = PacketUtil.getTime(byteBuf);
        byte replyFlag = byteBuf.getByte(3);
        if (time.equals(platformLoginPacketTime) && replyFlag == 1) {
            //平台登陆成功后设置可用状态
            setIsLogin(true);
            //恢复消费
            dataConsumer.resumeConsume();
            //启用心跳发送
            heartbeatFuture = manageExecutor.scheduleAtFixedRate(() -> {
                try {
                    ByteBuf buffer = PacketUtil.build_byteBuf_timeData(transferConfigData.uniqueCode, PacketFlag.heartbeat, 0xFE, new Date());
                    if (Const.logEnable) {
                        logger.info("--------------------------send heartbeat:\n{}", ByteBufUtil.hexDump(buffer));
                    }
                    send(buffer);
                } catch (Exception ex) {
                    logger.error("error", ex);
                }
            }, 3, 10, TimeUnit.SECONDS);
            //发送上线通知
            PlatformStatusData platformStatusData = new PlatformStatusData();
            platformStatusData.setStatus(1);
            platformStatusData.setServerId(transferConfigData.serverId);
            platformStatusData.setTime(new Date());
            platformStatusSender.send(platformStatusData);
            logger.info("platform login succeed sn[{}] time[{}]", platformLoginSn, DateZoneUtil.dateToStr_yyyyMMddHHmmss(time));
            //保存最后登陆时间
            redisTemplate.opsForValue().set(REDIS_KEY_PRE_PLATFORM_LAST_LOGIN_TIME + transferConfigData.serverId, DateZoneUtil.dateToStr_yyyyMMddHHmmss(time));
            //保存最后登陆的sn
            redisTemplate.opsForValue().set(REDIS_KEY_PRE_PLATFORM_LAST_LOGIN_SN + transferConfigData.serverId, platformLoginSn + "");
        }
    }

    private static void onPlatformLogoutResponse(ByteBuf byteBuf) {
        byte replyFlag = byteBuf.getByte(3);
        if (replyFlag == 1) {
            //设置不可用
            setIsLogin(false);
            //暂停消费
            dataConsumer.pauseConsume();
            //停止心跳发送
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
            //断开连接
            if (TcpClient.channel != null) {
                TcpClient.channel.close();
                TcpClient.channel = null;
            }
        }
    }

    private static void setIsLogin(boolean isLogin) {
        TcpClient.isLogin = isLogin;
        redisTemplate.opsForValue().set(REDIS_KEY_PRE_PLATFORM_STATUS + transferConfigData.serverId, String.valueOf(isLogin ? 1 : 0));
    }

    private static void onHeartbeatResponse(ByteBuf byteBuf) {
        Date time = PacketUtil.getTime(byteBuf);
        //保存最后心跳时间
        redisTemplate.opsForValue().set(REDIS_KEY_PRE_PLATFORM_LAST_HEARTBEAT_TIME + transferConfigData.serverId, DateZoneUtil.dateToStr_yyyyMMddHHmmss(time));
    }


    private static void send(ByteBuf byteBuf) {
        channel.writeAndFlush(byteBuf);
    }

    private static void startSendExecutor() {
        sendExecutor = Executors.newSingleThreadExecutor();
        sendExecutor.execute(() -> {
            while (true) {
                if (!isLogin) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                        continue;
                    } catch (InterruptedException ex) {
                        throw BaseException.get(ex);
                    }
                }
                try {
                    SendData sendData = sendQueue.take();
                    byte[] data = sendData.data();
                    if (Const.logEnable) {
                        logger.info("--------------------------send type[{}]:\n{}", PacketUtil.getPacketFlag(data), ByteBufUtil.hexDump(data));
                    }
                    send(Unpooled.wrappedBuffer(data));
                    sendData.callback();
                    sendNum.increment();
                } catch (Exception ex) {
                    logger.error("error", ex);
                }
            }
        });
    }

    private static void startScheduledExecutor(String platformCode) {
        manageExecutor = Executors.newSingleThreadScheduledExecutor();
        //启动定时任务、每天清除30天之前redis中的流水号数据
        manageExecutor.scheduleAtFixedRate(() -> {
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PRE_PLATFORM_SN + platformCode + ":");
            int i = Integer.parseInt(LocalDate.now().plusDays(-30).format(DateZoneUtil.FORMATTER_yyyyMMdd));
            for (String key : keys) {
                int temp = Integer.parseInt(key.substring(key.length() - 8));
                if (temp <= i) {
                    redisTemplate.delete(key);
                }
            }
        }, 0, 1, TimeUnit.DAYS);
    }

    private static int incrementAndGetSn(String platformCode) {
        return Objects.requireNonNull(redisTemplate.opsForValue().increment(REDIS_KEY_PRE_PLATFORM_SN + platformCode + ":" + DateZoneUtil.dateToStr_yyyyMMdd(new Date()))).intValue();
    }


    private static boolean platformLoginInternal() {
        try {
            //获取流水号
            TcpClient.platformLoginSn = incrementAndGetSn(TcpClient.transferConfigData.serverId);
            Packet packet = PacketUtil.build_packet_command_platformLogin(transferConfigData.uniqueCode, new Date(), TcpClient.platformLoginSn, TcpClient.transferConfigData.enterpriseName, transferConfigData.password);
            ByteBuf byteBuf = packet.toByteBuf_fixCode();
            logger.info("--------------------------send platformLogin:\n{}", ByteBufUtil.hexDump(byteBuf));
            send(byteBuf);
            TcpClient.platformLoginPacketTime = ((PlatformLoginData) packet.data).collectTime;
            return true;
        } catch (Exception ex) {
            logger.error("error", ex);
            return false;
        }
    }

    private static boolean platformLogoutInternal() {
        try {
            Packet packet = PacketUtil.build_packet_command_platformLogout(transferConfigData.uniqueCode, new Date(), TcpClient.platformLoginSn);
            ByteBuf byteBuf = packet.toByteBuf_fixCode();
            logger.info("--------------------------send platformLogout:\n{}", ByteBufUtil.hexDump(byteBuf));
            send(byteBuf);
            return true;
        } catch (Exception ex) {
            logger.error("error", ex);
            return false;
        }
    }


    private static CompletableFuture<Void> execute(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, manageExecutor);
    }

    private static <T> CompletableFuture<T> execute(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, manageExecutor);
    }

    public static void main(String[] args) {
        System.out.println(ParseUtil.getClassByteLenIfPossible(PlatformLoginData.class));
    }


}
