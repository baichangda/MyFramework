package cn.bcd.lib.spring.coap.client;

import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 带独立 UDP endpoint 的客户端，保留 Californium 的同步、异步和 Observe API。
 * 使用后调用 close 或 shutdown，释放本客户端的 endpoint 和内部线程。
 */
public final class CoapClient extends org.eclipse.californium.core.CoapClient implements AutoCloseable {
    private final CoapEndpoint clientEndpoint;
    private final AtomicBoolean closed = new AtomicBoolean();

    private CoapClient(Builder builder) throws IOException {
        super(builder.uri);
        Configuration configuration = new Configuration(CoapConfig.DEFINITIONS, UdpConfig.DEFINITIONS);
        configuration.set(UdpConfig.UDP_RECEIVER_THREAD_COUNT, 1);
        configuration.set(UdpConfig.UDP_SENDER_THREAD_COUNT, 1);
        builder.configurer.accept(configuration);
        clientEndpoint = new CoapEndpoint.Builder()
                .setConfiguration(configuration)
                .setInetSocketAddress(builder.localAddress)
                .build();
        try {
            clientEndpoint.start();
            super.setEndpoint(clientEndpoint);
            super.setTimeout(builder.timeoutMillis);
            if (!builder.confirmable) {
                super.useNONs();
            }
        } catch (IOException | RuntimeException e) {
            clientEndpoint.destroy();
            throw e;
        }
    }

    public static Builder builder(String uri) {
        return new Builder(uri);
    }

    /**
     * 与 close 相同，同时释放原生客户端和构造时创建的 endpoint。
     */
    @Override
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            try {
                super.shutdown();
            } finally {
                clientEndpoint.destroy();
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public static final class Builder {
        private final String uri;
        private long timeoutMillis = 5_000;
        private InetSocketAddress localAddress = new InetSocketAddress(0);
        private boolean confirmable = true;
        private Consumer<Configuration> configurer = configuration -> {};

        private Builder(String uri) {
            URI target = URI.create(Objects.requireNonNull(uri, "uri"));
            if (!"coap".equalsIgnoreCase(target.getScheme()) || target.getHost() == null
                    || target.getPort() == 0 || target.getPort() > 65535
                    || target.getUserInfo() != null || target.getFragment() != null) {
                throw new IllegalArgumentException("uri must be a UDP coap:// URI with a valid host and port");
            }
            this.uri = uri;
        }

        /** 同步请求超时毫秒数，默认 5000。 */
        public Builder timeoutMillis(long timeoutMillis) {
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeoutMillis must be greater than 0");
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /** 本地绑定地址，默认使用通配地址和临时端口。 */
        public Builder localAddress(String host, int port) {
            this.localAddress = new InetSocketAddress(Objects.requireNonNull(host, "host"), port);
            return this;
        }

        /** 是否发送 Confirmable 请求，默认 true。 */
        public Builder confirmable(boolean confirmable) {
            this.confirmable = confirmable;
            return this;
        }

        /** 自定义 Californium 配置；多次调用按添加顺序执行。 */
        public Builder configure(Consumer<Configuration> configurer) {
            this.configurer = this.configurer.andThen(Objects.requireNonNull(configurer, "configurer"));
            return this;
        }

        /** 构造并绑定独立 endpoint；绑定失败直接抛出 IOException。 */
        public CoapClient build() throws IOException {
            return new CoapClient(this);
        }
    }
}
