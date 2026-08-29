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

/**
 * 一次采集多个相互独立、无标签的 Prometheus Gauge 指标。
 *
 * <p>子类需要在构造方法中声明所有指标，并注册为 Spring Bean。每次 Prometheus
 * 抓取时，框架只调用一次 {@link #collectValues()}，其返回值必须按照指标的声明顺序
 * 一一对应。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Component
 * class QueueCollector extends MetricCollector {
 *     QueueCollector() {
 *         super(
 *                 new Metric("ready_queue_depth", "Current ready queue depth"),
 *                 new Metric("retry_queue_depth", "Current retry queue depth"));
 *     }
 *
 *     @Override
 *     protected double[] collectValues() {
 *         return new double[]{12, 3};
 *     }
 * }
 * }</pre>
 *
 * <p>上述 Collector 会输出 {@code ready_queue_depth 12.0} 和
 * {@code retry_queue_depth 3.0}。指标名称在同一个 Collector 中必须唯一，且
 * {@code collectValues()} 返回值数量必须与声明的指标数量相同。</p>
 */
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
