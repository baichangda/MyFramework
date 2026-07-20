# Lib-Spring-Data-Notify 使用指南

## 功能

基于 Kafka 提供车辆数据、平台状态、转发访问关系的单向通知，以及可订阅/取消订阅的客户端—服务端通知抽象。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Data-Notify')
```

```yaml
spring:
  kafka:
    bootstrap-servers: 127.0.0.1:9092
lib:
  spring:
    data:
      notify:
        vehicle-data:
          enable-sender: true
          group-id: vehicle-cache
```

设置 `enable-sender` 会创建对应 Sender；设置 `group-id` 会创建 Receiver。平台状态与转发访问分别使用 `platform-status`、`transfer-access` 节点。

```java
vehicleDataSender.send(vehicleData);
```

自定义请求式通知可继承 `AbstractNotifyClient` 或 `AbstractNotifyServer`，在生命周期中调用 `init()`，关闭时调用 `close()`/`destroy()`。生产者和消费者应使用互相兼容的数据模型与 Kafka 配置。
