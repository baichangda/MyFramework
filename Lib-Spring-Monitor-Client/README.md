# Lib-Spring-Monitor-Client 使用指南

## 功能

使用 OSHI 收集 CPU、内存、磁盘、网络等系统信息，并通过 Redis Topic 与监控服务交互。业务可通过 `MonitorExtCollector` 扩展采集内容。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Monitor-Client')
```

```yaml
lib:
  spring:
    monitor:
      client:
        request-topic: monitor-request
        response-list: monitor-response
        server-id: backend-01
        server-type: 1
        collect-cron: "0/30 * * * * ?"
```

同时配置 `spring.data.redis`，并确保模块包在 Spring 扫描范围内。直接采集可调用：

```java
SystemData data = MonitorUtil.collect();
```

`server-id` 在监控域内应唯一。采集频率不宜过高；扩展采集器应避免阻塞 Redis 消息线程或记录敏感环境信息。
