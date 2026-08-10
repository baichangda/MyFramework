# Lib-Spring-Redis 使用指南

## 功能

提供常用 RedisTemplate、JSON/字节/整数序列化器、Topic 与 Queue 消息封装、Redis 限流器、服务注册发现，以及基于 Redis 锁的单实例定时任务保护。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Redis')
```

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

确保 `RedisConfig` 被扫描，可注入 `string_serializable_redisTemplate`、`string_string_redisTemplate` 和 `RedisMessageListenerContainer`。自定义类型模板可通过 `RedisUtil.newRedisTemplate_string_jackson(...)` 创建。

`RedisTopicMQ` 用于广播，`RedisQueueMQ` 用于队列消费；创建后调用 `init()`，关闭时调用 `close()`。使用 `@SingleFailedSchedule` 前需启用 AOP 和调度。服务注册功能通过 `register.host` 启用。

服务注册配置示例：

```yaml
register:
  host: 10.0.0.1:8080
  servers:
    - test1
```

注册信息使用 `register:v2:{service}` Sorted Set 保存，并以 Redis 服务器时间作为心跳分值。应用只消费服务时可将 `register.host` 配置为空字符串。注册心跳随 Spring 生命周期自动启动和停止，无需手动初始化或关闭。

旧版 `register:{service}` Hash 与新版数据结构不兼容；升级时应协调同一服务的提供者和消费者，避免新旧版本互相不可见。

Redis key、序列化类型和消息版本必须在生产者与消费者之间保持一致。
