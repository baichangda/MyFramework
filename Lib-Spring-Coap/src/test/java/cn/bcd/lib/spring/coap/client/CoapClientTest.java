package cn.bcd.lib.spring.coap.client;

import org.eclipse.californium.core.config.CoapConfig;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class CoapClientTest {
    @Test
    void buildsIndependentEndpointsWithTimeoutAndCustomConfiguration() throws Exception {
        CoapClient.Builder builder = CoapClient.builder("coap://127.0.0.1:5683/health")
                .timeoutMillis(1_500)
                .localAddress("127.0.0.1", 0)
                .confirmable(false)
                .configure(config -> config.set(CoapConfig.PREFERRED_BLOCK_SIZE, 512))
                .configure(config -> config.set(CoapConfig.MAX_RESOURCE_BODY_SIZE, 16_384));
        try (CoapClient first = builder.build(); CoapClient second = builder.build()) {
            assertEquals("coap://127.0.0.1:5683/health", first.getURI());
            assertEquals(1_500L, first.getTimeout());
            assertTrue(first.getEndpoint().isStarted());
            assertNotSame(first.getEndpoint(), second.getEndpoint());
            assertNotEquals(first.getEndpoint().getAddress().getPort(), second.getEndpoint().getAddress().getPort());
            assertEquals(512, first.getEndpoint().getConfig().get(CoapConfig.PREFERRED_BLOCK_SIZE));
            assertEquals(16_384, first.getEndpoint().getConfig().get(CoapConfig.MAX_RESOURCE_BODY_SIZE));
        }
    }

    @Test
    void shutdownReleasesPortAndCloseIsIdempotent() throws Exception {
        CoapClient client = CoapClient.builder("coap://127.0.0.1/health")
                .localAddress("127.0.0.1", 0).build();
        int port = client.getEndpoint().getAddress().getPort();
        try {
            client.shutdown();
            assertFalse(client.getEndpoint().isStarted());
            assertDoesNotThrow(client::close);
            try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", port))) {
                assertEquals(port, socket.getLocalPort());
            }
        } finally {
            client.close();
        }
    }

    @Test
    void bindFailureIsPropagatedToCaller() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            assertThrows(java.io.IOException.class, () -> CoapClient.builder("coap://127.0.0.1/health")
                    .localAddress("127.0.0.1", socket.getLocalPort()).build());
        }
    }

    @Test
    void rejectsUnsupportedUriAndInvalidTimeout() {
        for (String uri : new String[]{"http://127.0.0.1", "coaps://127.0.0.1", "coap:///health",
                "coap://127.0.0.1:0", "coap://127.0.0.1:65536", "coap://user@127.0.0.1", "coap://127.0.0.1/#fragment"}) {
            assertThrows(IllegalArgumentException.class, () -> CoapClient.builder(uri));
        }
        assertThrows(IllegalArgumentException.class, () -> CoapClient.builder("coap://127.0.0.1").timeoutMillis(0));
    }
}
