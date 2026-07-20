# Lib-Spring-Storage-Influxdb 使用指南

## 功能

配置 InfluxDB 3 Java 客户端，并提供 GB32960 原始报文的批量保存、单条查询和时间范围分页查询。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Storage-Influxdb')
```

```yaml
lib:
  spring:
    storage:
      influxdb:
        url: http://127.0.0.1:8181
        token: ${INFLUX_TOKEN}
        database: vehicle
```

配置 `url` 后创建客户端。典型调用：

```java
InfluxdbUtil_gb32960.save_rawData(rawDataList);
List<RawData> page = InfluxdbUtil_gb32960.page_rawData(
        vin, begin, end, 0, 100, true);
```

时间范围、排序和 offset 会直接影响查询成本；大数据量翻页应做性能验证。Token 不得提交到仓库，应用关闭时应由 Spring 管理客户端生命周期。
