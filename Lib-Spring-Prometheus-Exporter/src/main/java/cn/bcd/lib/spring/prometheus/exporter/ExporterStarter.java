package cn.bcd.lib.spring.prometheus.exporter;

import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@ConditionalOnProperty("lib.spring.prometheus.exporter.host")
@EnableConfigurationProperties(ExporterProp.class)
@Component
class ExporterStarter implements CommandLineRunner, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ExporterStarter.class);
    private final ExporterProp exporterProp;
    private final List<MetricCollector> collectors;
    private final PrometheusRegistry registry = new PrometheusRegistry();
    private HTTPServer server;

    ExporterStarter(ExporterProp exporterProp, List<MetricCollector> collectors) {
        this.exporterProp = exporterProp;
        this.collectors = List.copyOf(collectors);
    }

    @Override
    public void run(String... args) throws Exception {
        exporterProp.validate();
        if (server != null) {
            throw new IllegalStateException("prometheus exporter is already running");
        }
        JvmMetrics.builder().register(registry);
        collectors.forEach(e -> e.register(registry));
        server = HTTPServer.builder()
                .hostname(exporterProp.getHost())
                .port(exporterProp.getPort())
                .registry(registry)
                .buildAndStart();
        logger.info("prometheus exporter started, listening on http://{}:{}/metrics, found {} collectors: {}",
                exporterProp.getHost(),
                server.getPort(),
                collectors.size(),
                collectors.stream().map(e -> e.getClass().getName()).toList());
    }

    int getPort() {
        if (server == null) {
            throw new IllegalStateException("prometheus exporter is not running");
        }
        return server.getPort();
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
            server = null;
            logger.info("prometheus exporter stopped");
        }
    }
}
