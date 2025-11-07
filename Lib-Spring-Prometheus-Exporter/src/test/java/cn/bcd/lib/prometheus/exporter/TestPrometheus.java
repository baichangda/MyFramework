package cn.bcd.lib.prometheus.exporter;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.snapshots.Unit;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestPrometheus {
    @Test
    public void test1() throws IOException, InterruptedException {
        JvmMetrics.builder().register();


        Counter counter = Counter.builder()
                .name("uptime_seconds_total")
                .help("total number of seconds since this application was started")
                .unit(Unit.SECONDS)
                .register();

        Gauge gauge = Gauge.builder()
                .name("cpu_usage")
                .help("cpu usage")
                .register();

        HTTPServer server = HTTPServer.builder().port(19400).buildAndStart();
        System.out.println(
                "HTTPServer listening on port http://localhost:" + server.getPort() + "/metrics");


        while (true) {
            Thread.sleep(1000);
            counter.inc();
        }
    }
}
