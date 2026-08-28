package cn.bcd.app.transponder.gb32960.v2025;

import cn.bcd.app.transponder.gb32960.TcpServer;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "v2025", mixinStandardHelpOptions = true)
public class Starter_v2025 extends TcpServer {
    private static final Logger logger = LoggerFactory.getLogger(Starter_v2025.class);
    private static final int MAX_FRAME_LENGTH = 10_240;

    @CommandLine.Option(names = "--key-store", defaultValue = "server.jks", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String keyStorePath;

    @CommandLine.Option(names = "--trust-store", defaultValue = "ca.jks", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String trustStorePath;

    @CommandLine.Option(names = "--store-password", description = "JKS password; alternatively set GB32960_STORE_PASSWORD")
    private String storePassword;

    @CommandLine.Option(names = "--key-password", description = "private-key password; defaults to store password")
    private String keyPassword;

    @CommandLine.Option(names = "--trust-store-password", description = "trust-store password; defaults to store password")
    private String trustStorePassword;

    @Override
    protected ChannelHandler createChildHandler() {
        String password = firstNonBlank(storePassword, System.getenv("GB32960_STORE_PASSWORD"));
        if (password == null) {
            throw BaseException.get("--store-password or GB32960_STORE_PASSWORD is required");
        }
        String actualKeyPassword = firstNonBlank(keyPassword, password);
        String actualTrustStorePassword = firstNonBlank(trustStorePassword, password);
        try {
            KeyManagerFactory kmf = SslUtils.loadKeyManagerFactory(keyStorePath, password, actualKeyPassword);
            TrustManagerFactory tmf = SslUtils.loadTrustManagerFactory(trustStorePath, actualTrustStorePassword);
            SslContext sslContext = SslContextBuilder
                    .forServer(kmf)
                    .trustManager(tmf)
                    .clientAuth(ClientAuth.REQUIRE)
                    .sslProvider(SslProvider.JDK)
                    .protocols("TLSv1.2")
                    .build();
            return new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                    ch.pipeline().addLast(new IdleStateHandler(0L, 0L, 30L, TimeUnit.SECONDS));
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof IdleStateEvent event && event.state() == IdleState.ALL_IDLE) {
                                logger.info("close idle client [{}]", ctx.channel().remoteAddress());
                                ctx.close();
                                return;
                            }
                            super.userEventTriggered(ctx, evt);
                        }
                    });
                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 22, 2, 1, 0));
                    ch.pipeline().addLast(new DataInboundHandler_v2025());
                }
            };
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

}
