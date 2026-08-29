package cn.bcd.lib.spring.prometheus.exporter;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class MetricCollector {
    final Logger logger = LoggerFactory.getLogger(this.getClass());

    public final String[] labels;
    public final GaugeWithCallback gaugeWithCallback;

    public MetricCollector(String... labels) {
        this.labels = labels;
        this.gaugeWithCallback = GaugeWithCallback.builder()
                .labelNames()
                .callback(callback -> {
                    double[] vals = collect();
                    if (vals.length != labels.length) {
                        logger.error("vals len[{}] not equals labels len[{}]", vals.length, labels.length);
                        return;
                    }
                    for (int i = 0; i < labels.length; i++) {
                        callback.call(vals[i], labels[i]);
                    }
                })
                .register();
    }

    /**
     * 对应{@link #labels}的值
     * @return
     */
    public abstract double[] collect();
}
