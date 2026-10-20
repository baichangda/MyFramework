# Lib-Spring-Monitor-Client 使用指南

客户端接收 App-Monitor-Collector 的 Redis Topic 请求，使用 OSHI 采集系统指标，并将带有服务 ID、服务类型和请求批次的响应写入本批次独立的 Redis List。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Monitor-Client')
```

```yaml
lib:
  spring:
    monitor:
      client:
        request-topic: monitorRequest
        response-list: monitorResponse
        server-id: backend-01
        server-type: 1
```

同时配置 `spring.data.redis`，并确保模块包在 Spring 扫描范围内。两端必须连接同一个 Redis 监控域，并使用相同的 topic 和 response-list。前两项默认值与上面的示例一致。

`server-id` 必须非空且在监控域内唯一，并预先登记到采集端数据库的 `t_server_data`；采集端只保存名单内的服务。未配置 server-id 时仅提供广播能力，不订阅采集请求，因此 Collector 无需配置自己的 server-id。

## 扩展采集

业务可提供一个 `MonitorExtCollector` Bean。采集器注入完成后才开始订阅请求。系统采集会阻塞约一秒；随后在当前消息处理线程同步调用扩展采集器，完成后上报。扩展抛异常仍上报系统指标，extData 为空。

扩展采集器应为外部调用自行配置超时，并在 Collector 的十秒响应窗口内完成；阻塞会延迟当前上报和后续请求处理。不要在扩展数据中记录敏感信息。

直接采集系统指标可调用 `MonitorUtil.collect()`。

## 协议与升级

请求体为正数批次号字符串；响应包括 serverId、serverType、batch、systemData 和可选的 extData。Redis 响应键为 `<response-list>:<batch>`，写入与设置一天 TTL 通过单键 Lua 脚本原子完成，兼容 Redis Cluster。

本协议与旧版共享响应 List 不兼容，客户端及 Collector 必须一起升级。完整部署及重试行为见 [Collector 使用指南](../App-Monitor-Collector/README.md)。
