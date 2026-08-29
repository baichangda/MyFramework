# Lib-Spring-Prometheus-Exporter 使用指南

## 功能

启动 Prometheus HTTP 指标服务并注册 JVM 指标。一个 `MetricCollector` 可以在一次采集中暴露多个相互独立、没有标签的 Gauge 指标。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Prometheus-Exporter')
```

```yaml
lib:
  spring:
    prometheus:
      exporter:
        host: 0.0.0.0
        port: 9400
```

配置 `host` 后 Exporter 才会启动。`port` 默认是 `9400`；设置为 `0` 时由系统选择空闲端口。

## 自定义指标

构造方法中声明指标名称和说明，`collectValues()` 按照相同顺序返回指标值：

```java
@Component
class QueueCollector extends MetricCollector {
    QueueCollector() {
        super(
                new Metric("ready_queue_depth", "Current ready queue depth"),
                new Metric("retry_queue_depth", "Current retry queue depth"));
    }

    @Override
    protected double[] collectValues() {
        return new double[]{12, 3};
    }
}
```

输出结果：

```text
ready_queue_depth 12.0
retry_queue_depth 3.0
```

一次 Prometheus 抓取只会调用一次 `collectValues()`。返回值数量必须与声明的 `Metric` 数量一致，否则本次抓取会失败并报告明确错误。指标名称必须唯一、稳定并符合 Prometheus 命名规范。
