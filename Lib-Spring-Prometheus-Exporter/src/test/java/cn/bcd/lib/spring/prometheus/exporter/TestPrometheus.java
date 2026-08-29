package cn.bcd.lib.spring.prometheus.exporter;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPrometheus {
    @Test
    void exposesMultipleIndependentMetricsWithOneCollection() throws Exception {
        ExporterProp prop = new ExporterProp();
        prop.setHost("127.0.0.1");
        prop.setPort(0);
        AtomicInteger collectionCount = new AtomicInteger();
        MetricCollector collector = new MetricCollector(
                new MetricCollector.Metric("ready_queue_depth", "Current ready queue depth"),
                new MetricCollector.Metric("retry_queue_depth", "Current retry queue depth")) {
            @Override
            protected double[] collectValues() {
                collectionCount.incrementAndGet();
                return new double[]{12, 3};
            }
        };
        ExporterStarter starter = new ExporterStarter(prop, List.of(collector));
        try {
            starter.run();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + starter.getPort() + "/metrics"))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("ready_queue_depth 12.0"));
            assertTrue(response.body().contains("retry_queue_depth 3.0"));
            assertTrue(response.body().contains("jvm_threads_current"));
            assertEquals(1, collectionCount.get());
        } finally {
            starter.close();
        }
    }
}
