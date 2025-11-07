package cn.bcd.lib.spring.prometheus.exporter;

import cn.bcd.lib.base.exception.BaseException;
import io.prometheus.metrics.core.metrics.Gauge;

public class ExporterPoints {
    public final Point[] points;
    public final Gauge[] gauges;

    public ExporterPoints(Point... points) {
        this.points = points;
        gauges = new Gauge[points.length];
        for (int i = 0; i < points.length; i++) {
            Point point = points[i];
            gauges[i] = Gauge.builder()
                    .name(point.name)
                    .help(point.help)
                    .register();
        }
    }

    public void set(double... vals) {
        if (vals.length != points.length) {
            throw BaseException.get("points len[{}] not equal vals len[{}]", points.length, vals.length);
        }
        for (int i = 0; i < points.length; i++) {
            gauges[i].set(vals[i]);
        }
    }

    public record Point(String name, String help) {

    }
}
