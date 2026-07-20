# Lib-Spring-Vehicle-Command 使用指南

## 功能

基于 Kafka 和 Redis 实现车辆指令请求/响应链路，包括在线状态判断、车辆级锁、超时回调，以及 GB32960-2016 参数查询命令示例。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Vehicle-Command')
```

```yaml
lib:
  spring:
    vehicle:
      command:
        sender-group-id: command-sender
        receiver-group-id: command-receiver
        request-topic: vehicle-command-request
        response-topic: vehicle-command-response
```

发送端配置 `sender-group-id`，接收端配置 `receiver-group-id`；两端还需配置相同 Kafka 集群和可访问的 Redis。

自定义命令继承 `Command<R>`，实现 `toRequestBytes()` 与 `toResponse(byte[])`，再通过 `CommandSender.send(...)` 提交并处理 `CommandCallback`。发送前可调用 `CommandSender.online(vin)`。

同一车辆并发指令涉及分布式锁和超时，调用方必须处理失败状态、重复响应及幂等性。
