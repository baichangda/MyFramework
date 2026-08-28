package cn.bcd.app.transponder.gb32960.v2025;

import cn.bcd.app.transponder.gb32960.TcpServer;
import cn.bcd.lib.base.exception.BaseException;
import io.netty.channel.Channel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import picocli.CommandLine;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

@CommandLine.Command(name = "v2025", mixinStandardHelpOptions = true)
public class Starter_v2025 extends TcpServer {
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

    private SslContext sslContext;

    @Override
    protected void beforeStart() {
        String password = firstNonBlank(storePassword, System.getenv("GB32960_STORE_PASSWORD"));
        if (password == null) {
            throw BaseException.get("--store-password or GB32960_STORE_PASSWORD is required");
        }
        String actualKeyPassword = firstNonBlank(keyPassword, password);
        String actualTrustStorePassword = firstNonBlank(trustStorePassword, password);
        try {
            KeyManagerFactory kmf = SslUtils.loadKeyManagerFactory(keyStorePath, password, actualKeyPassword);
            TrustManagerFactory tmf = SslUtils.loadTrustManagerFactory(trustStorePath, actualTrustStorePassword);
            sslContext = SslContextBuilder
                    .forServer(kmf)
                    .trustManager(tmf)
                    .clientAuth(ClientAuth.REQUIRE)
                    .sslProvider(SslProvider.JDK)
                    .protocols("TLSv1.2")
                    .build();
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    @Override
    protected void initTransport(Channel ch) {
        ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
    }

    @Override
    protected void init(Channel ch) {
        ch.pipeline().addLast(new DataInboundHandler_v2025());
    }
}
