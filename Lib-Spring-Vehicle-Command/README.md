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

请求 ID 和 Kafka 请求/响应 key 使用“报文秒级时间戳,VIN,flag”。时间取自指令数据单元前 6 字节；网关从响应报文中读取相同时间，匹配对应请求。调用方须保证同一秒内不重复发送相同 VIN、flag 的指令。Redis 指令锁仍仅按 VIN、flag 互斥。

发送端与 TCP/MQTT 网关需同步更新，以使用相同的请求 ID 和缓存键格式。

同一车辆并发指令涉及分布式锁和超时，调用方必须处理失败状态、重复响应及幂等性。
