package cn.bcd.lib.spring.coap.server;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.UdpConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lib.spring.coap.server", name = "host")
@EnableConfigurationProperties(CoapServerProp.class)
public class CoapConfiguration {
    @Autowired(required = false)
    private List<CoapResource> resources = List.of();

    @Bean(destroyMethod = "destroy")
    public CoapServer coapServer(CoapServerProp prop) {
        var configuration = new org.eclipse.californium.elements.config.Configuration(
                CoapConfig.DEFINITIONS, UdpConfig.DEFINITIONS);
        configuration.set(CoapConfig.PROTOCOL_STAGE_THREAD_COUNT, prop.getProtocolThreadCount());
        CoapEndpoint endpoint = new CoapEndpoint.Builder()
                .setConfiguration(configuration)
                .setInetSocketAddress(new InetSocketAddress(prop.getHost(), prop.getPort()))
                .build();
        CoapServer server = new CoapServer(configuration);
        server.addEndpoint(endpoint);
        resources.forEach(server::add);
        return server;
    }

    @Bean
    public CoapServerLifecycle coapServerLifecycle(CoapServer server) {
        return new CoapServerLifecycle(server);
    }
}
