package cn.bcd.lib.spring.coap.server;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapServer;
import org.springframework.context.SmartLifecycle;

@Slf4j
public class CoapServerLifecycle implements SmartLifecycle {
    private final CoapServer server;

    public CoapServerLifecycle(CoapServer server) {
        this.server = server;
    }

    @Override
    public void start() {
        try {
            server.start();
            server.getEndpoints().forEach(endpoint -> log.info("CoAP server listening on {}", endpoint.getUri()));
        } catch (RuntimeException e) {
            server.destroy();
            throw e;
        }
    }

    @Override
    public void stop() {
        server.stop();
    }

    @Override
    public boolean isRunning() {
        return server.isRunning();
    }
}
