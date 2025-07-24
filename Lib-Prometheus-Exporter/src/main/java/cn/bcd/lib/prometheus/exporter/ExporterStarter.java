package cn.bcd.lib.prometheus.exporter;

import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@ConditionalOnProperty("lib.prometheus.exporter.port")
@EnableConfigurationProperties(ExporterProp.class)
@Component
public class ExporterStarter implements CommandLineRunner {

    static Logger logger = LoggerFactory.getLogger(ExporterStarter.class);

    @Autowired
    ExporterProp exporterProp;

    @Override
    public void run(String... args) throws Exception {
        JvmMetrics.builder().register();
        HTTPServer.builder().port(exporterProp.port).buildAndStart();
        logger.info("prometheus exporter started、listen on http://127.0.0.1:{}/metrics", exporterProp.port);
    }
}
