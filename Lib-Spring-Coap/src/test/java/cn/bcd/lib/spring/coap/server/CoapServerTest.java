package cn.bcd.lib.spring.coap.server;

import cn.bcd.lib.spring.coap.server.resource.EchoResource;
import cn.bcd.lib.spring.coap.server.resource.HealthResource;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoapServerTest {
    @Test
    void yamlEnablesScannedConfigurationAndInjectsBusinessResources() throws Exception {
        CoapServer server;
        int port;
        try (ConfigurableApplicationContext context = application().sources(ResourceBeans.class)
                .run("--spring.config.name=coap-test")) {
            server = context.getBean(CoapServer.class);
            assertTrue(server.isRunning());
            assertEquals(2, context.getBean(ResourceList.class).resources().size());
            assertEquals(1, server.getEndpoints().size());
            port = server.getEndpoints().getFirst().getAddress().getPort();
            assertTrue(port > 0);
            String base = "coap://127.0.0.1:" + port;
            Configuration configuration = new Configuration(CoapConfig.DEFINITIONS, UdpConfig.DEFINITIONS);
            CoapEndpoint endpoint = new CoapEndpoint.Builder()
                    .setConfiguration(configuration)
                    .setInetSocketAddress(new InetSocketAddress("127.0.0.1", 0))
                    .build();
            CoapClient client = new CoapClient().setEndpoint(endpoint).setTimeout(3_000L);
            try {
                endpoint.start();
                CoapResponse health = client.setURI(base + "/health").get();
                assertNotNull(health);
                assertEquals(ResponseCode.CONTENT, health.getCode());
                assertEquals("UP", health.getResponseText());

                byte[] payload = new byte[4096];
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) i;
                }
                CoapResponse echo = client.setURI(base + "/echo")
                        .post(payload, MediaTypeRegistry.APPLICATION_OCTET_STREAM);
                assertNotNull(echo);
                assertEquals(ResponseCode.CONTENT, echo.getCode());
                assertArrayEquals(payload, echo.getPayload());
                assertEquals(MediaTypeRegistry.APPLICATION_OCTET_STREAM, echo.getOptions().getContentFormat());

                echo = client.post("中文回显", MediaTypeRegistry.TEXT_PLAIN);
                assertNotNull(echo);
                assertEquals("中文回显", echo.getResponseText());
                assertEquals(MediaTypeRegistry.TEXT_PLAIN, echo.getOptions().getContentFormat());

                CoapResponse unsupported = client.get();
                assertNotNull(unsupported);
                assertEquals(ResponseCode.METHOD_NOT_ALLOWED, unsupported.getCode());

                CoapResponse discovery = client.setURI(base + "/.well-known/core").get();
                assertNotNull(discovery);
                assertTrue(discovery.getResponseText().contains("</health>"));
                assertTrue(discovery.getResponseText().contains("</echo>"));
            } finally {
                client.shutdown();
                endpoint.destroy();
            }
        }
        assertFalse(server.isRunning());
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", port))) {
            assertEquals(port, socket.getLocalPort());
        }
    }

    @Test
    void doesNotStartServerByDefault() {
        try (ConfigurableApplicationContext context = application().run()) {
            assertTrue(context.getBeansOfType(CoapServer.class).isEmpty());
            assertTrue(context.getBeansOfType(CoapServerLifecycle.class).isEmpty());
        }
    }

    @Test
    void falseHostPropertyDoesNotStartServer() {
        try (ConfigurableApplicationContext context = application()
                .run("--spring.config.name=coap-test", "--lib.spring.coap.server.host=false")) {
            assertTrue(context.getBeansOfType(CoapServer.class).isEmpty());
            assertTrue(context.getBeansOfType(CoapServerLifecycle.class).isEmpty());
        }
    }

    @Test
    void canStartWithoutResourceBeans() {
        try (ConfigurableApplicationContext context = application().run("--spring.config.name=coap-test")) {
            CoapServer server = context.getBean(CoapServer.class);
            assertTrue(server.isRunning());
            assertTrue(context.getBeansOfType(CoapResource.class).isEmpty());
            assertNull(server.getRoot().getChild("health"));
            assertNull(server.getRoot().getChild("echo"));
        }
    }

    @Test
    void failsStartupWhenPortIsAlreadyBound() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            assertThrows(RuntimeException.class, () -> application().run("--spring.config.name=coap-test",
                    "--lib.spring.coap.server.port=" + socket.getLocalPort()));
        }
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = CoapConfiguration.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CoapConfiguration.class))
    static class TestApplication {
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class ResourceBeans {
        @Bean
        HealthResource healthResource() {
            return new HealthResource();
        }

        @Bean
        EchoResource echoResource() {
            return new EchoResource();
        }

        @Bean
        ResourceList resourceList(List<CoapResource> resources) {
            return new ResourceList(resources);
        }
    }

    record ResourceList(List<CoapResource> resources) {
    }
}
