# Lib-Spring-Prometheus-Exporter 使用指南

## 功能

启动 Prometheus HTTP 指标服务并注册 JVM 指标。`ExporterPoints` 可将一组自定义无标签 Gauge 统一创建和更新。

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

配置 `host` 后 `ExporterStarter` 才会启动。自定义指标示例：

```java
ExporterPoints points = new ExporterPoints(
        new ExporterPoints.Point("queue_depth", "Current queue depth"));
points.set(12);
```

`set` 的值数量应与 Point 数量一致。指标名应稳定、符合 Prometheus 命名习惯，且不要把 VIN、用户 ID 等高基数字段编码到指标名。生产环境需限制指标端口的网络访问。
