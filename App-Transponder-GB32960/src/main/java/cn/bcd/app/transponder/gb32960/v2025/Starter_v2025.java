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

    // 证书路径（根据实际情况修改）
    private static final String SERVER_KEY_STORE = "server.jks";
    private static final String TRUST_STORE = "ca.jks";
    private static final String PASSWORD = "123456";

    SslContext sslContext;

    public Starter_v2025() {
        initSslContext();
    }

    private void initSslContext() {
        try {
            // 1. 加载服务器密钥库（自身证书+私钥）
            KeyManagerFactory kmf = SslUtils.loadKeyManagerFactory(SERVER_KEY_STORE, PASSWORD, PASSWORD);
            // 2. 加载信任库（信任CA根证书，用于验证客户端）
            TrustManagerFactory tmf = SslUtils.loadTrustManagerFactory(TRUST_STORE, PASSWORD);

            // 3. 构建SslContext（服务器端），强制客户端认证
            sslContext = SslContextBuilder
                    .forServer(kmf) // 服务器自身证书和私钥
                    .trustManager(tmf) // 信任的CA（用于验证客户端）
                    .clientAuth(ClientAuth.REQUIRE) // 强制要求客户端发送证书
                    .sslProvider(SslProvider.JDK) // 使用JDK的SSL实现
                    .protocols("TLSv1.2")
                    .build();
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }


    @Override
    protected void init(Channel ch) {
        ch.pipeline().addLast(new DataInboundHandler_v2025());
    }
}
