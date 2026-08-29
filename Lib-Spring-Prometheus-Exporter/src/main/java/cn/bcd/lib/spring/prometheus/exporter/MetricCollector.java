package cn.bcd.lib.spring.prometheus.exporter;

import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public abstract class MetricCollector implements MultiCollector {
    private final List<Metric> metrics;
    private final List<MetricFamilyDescriptor> descriptors;

    protected MetricCollector(Metric... metrics) {
        Objects.requireNonNull(metrics, "metrics");
        if (metrics.length == 0) {
            throw new IllegalArgumentException("at least one metric is required");
        }
        this.metrics = List.copyOf(Arrays.asList(metrics.clone()));
        var names = new HashSet<String>();
        for (Metric metric : this.metrics) {
            Objects.requireNonNull(metric, "metric");
            if (!names.add(metric.name())) {
                throw new IllegalArgumentException("duplicate metric name: " + metric.name());
            }
        }
        this.descriptors = this.metrics.stream()
                .map(e -> MetricFamilyDescriptor.gauge(e.name()).help(e.help()).build())
                .toList();
    }

    final void register(PrometheusRegistry registry) {
        Objects.requireNonNull(registry, "registry").register(this);
    }

    @Override
    public final MetricSnapshots collect() {
        double[] values = collectValues();
        if (values == null || values.length != metrics.size()) {
            throw new IllegalStateException("collector returned "
                    + (values == null ? null : values.length)
                    + " values, expected " + metrics.size());
        }
        MetricSnapshots.Builder snapshots = MetricSnapshots.builder();
        for (int i = 0; i < metrics.size(); i++) {
            Metric metric = metrics.get(i);
            snapshots.metricSnapshot(GaugeSnapshot.builder()
                    .name(metric.name())
                    .help(metric.help())
                    .dataPoint(new GaugeSnapshot.GaugeDataPointSnapshot(values[i], Labels.EMPTY, null))
                    .build());
        }
        return snapshots.build();
    }

    @Override
    public final List<MetricFamilyDescriptor> getMetricFamilyDescriptors() {
        return descriptors;
    }

    /**
     * 按构造参数中 Metric 的声明顺序返回每个独立指标的当前值。
     */
    protected abstract double[] collectValues();

    public record Metric(String name, String help) {
        public Metric {
            name = requireText(name, "name");
            help = requireText(help, "help");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
