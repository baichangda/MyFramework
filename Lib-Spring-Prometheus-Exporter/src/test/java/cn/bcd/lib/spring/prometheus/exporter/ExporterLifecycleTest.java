package cn.bcd.lib.spring.prometheus.exporter;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExporterLifecycleTest {
    @Test
    void doesNotExportBuiltinJvmMetrics() throws Exception {
        try (ExporterStarter starter = new ExporterStarter(properties(), List.of())) {
            starter.run();
            HttpResponse<String> response = scrape(starter);
            assertEquals(200, response.statusCode());
            assertFalse(response.body().contains("jvm_"));
            assertFalse(response.body().contains("process_"));
        }
    }

    @Test
    void sameExporterCanRestartAfterClose() throws Exception {
        MetricCollector collector = gauge("restart_value", () -> new double[]{9});
        try (ExporterStarter starter = new ExporterStarter(properties(), List.of(collector))) {
            for (int i = 0; i < 3; i++) {
                starter.run();
                assertHealthy(scrape(starter), "restart_value 9.0");
                starter.close();
                starter.close();
                assertThrows(IllegalStateException.class, starter::getPort);
            }
        }
    }

    @Test
    void failedPortBindingCanBeRetriedWithoutDuplicateRegistration() throws Exception {
        ExporterProp prop = properties();
        MetricCollector collector = gauge("retry_start_value", () -> new double[]{5});
        try (ExporterStarter starter = new ExporterStarter(prop, List.of(collector))) {
            try (ServerSocket occupied = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
                prop.setPort(occupied.getLocalPort());
                assertThrows(java.io.IOException.class, starter::run);
            }
            starter.run();
            assertHealthy(scrape(starter), "retry_start_value 5.0");
        }
    }

    @Test
    void failingCollectorIsOmittedAndRecoversOnNextSuccessfulScrape() throws Exception {
        AtomicInteger mode = new AtomicInteger();
        MetricCollector failing = gauge("isolated_value", () -> switch (mode.get()) {
            case 0 -> throw new IllegalStateException("failed");
            case 1 -> null;
            case 2 -> new double[]{1, 2};
            default -> new double[]{11};
        });
        MetricCollector healthy = gauge("healthy_value", () -> new double[]{3});
        try (ExporterStarter starter = new ExporterStarter(properties(), List.of(failing, healthy))) {
            starter.run();
            for (int i = 0; i < 3; i++) {
                mode.set(i);
                HttpResponse<String> response = scrape(starter);
                assertHealthy(response, "healthy_value 3.0");
                assertFalse(response.body().contains("isolated_value"), "Failed values must not be replaced by zero");
            }
            mode.set(3);
            HttpResponse<String> response = scrape(starter);
            assertHealthy(response, "healthy_value 3.0");
            assertTrue(response.body().contains("isolated_value 11.0"));
        }
    }

    @Test
    void duplicateBusinessMetricsAreStillRejectedAtRegistration() {
        MetricCollector first = gauge("duplicate_value", () -> new double[]{1});
        MetricCollector conflicting = gauge("duplicate_value", () -> new double[]{2});
        try (ExporterStarter starter = new ExporterStarter(properties(), List.of(first, conflicting))) {
            assertThrows(IllegalArgumentException.class, starter::run);
            assertThrows(IllegalStateException.class, starter::getPort);
        }
    }

    private static MetricCollector gauge(String name, java.util.function.Supplier<double[]> values) {
        return new MetricCollector(new MetricCollector.Metric(name, "Test metric")) {
            @Override
            protected double[] collectValues() {
                return values.get();
            }
        };
    }

    private static ExporterProp properties() {
        ExporterProp prop = new ExporterProp();
        prop.setHost("127.0.0.1");
        prop.setPort(0);
        return prop;
    }

    private static HttpResponse<String> scrape(ExporterStarter starter) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + starter.getPort() + "/metrics"))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertHealthy(HttpResponse<String> response, String expected) {
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(expected));
    }

}
