package cn.bcd.lib.prometheus.exporter;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;

public record ExporterPoint(String name, String label, Number value) {
    public void register() {
        GaugeWithCallback.builder()
                .callback(callback -> {
                    callback.call(value.doubleValue());
                })
                .name(name)
                .labelNames(label)
                .register();
    }
}
