# Lib-Spring-Kafka 使用指南

## 功能

提供 String/byte[] KafkaTemplate、原生生产者与消费者工厂，以及线程驱动、数据 ID 驱动的扩展消费者。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Kafka')
```

```yaml
spring:
  kafka:
    bootstrap-servers: 127.0.0.1:9092
    consumer:
      group-id: demo
```

确保 `KafkaConfig` 被 Spring 扫描后，可按 Bean 名注入 `string_bytes_kafkaTemplate` 或 `string_string_kafkaTemplate`。独立客户端可使用：

```java
KafkaProducer<String, byte[]> producer =
        KafkaUtil.newKafkaProducer_string_bytes(properties);
```

需要同一业务 ID 串行消费时继承 `DataDrivenKafkaConsumer` 并实现 `newHandler`；普通并行消费可继承 `ThreadDrivenKafkaConsumer` 并实现 `onMessage`。启动后必须在应用关闭时调用 `close()`，并明确 offset、重试和幂等策略。

```shell
gradle :Lib-Spring-Kafka:test
```

ThreadDrivenKafkaConsumer 通过 `id(record)` 获取业务 ID（默认为消息 key），调用 `getWorkExecutor(id)` 选择执行器，再提交处理任务。默认相同 ID 串行处理，空 ID 使用第一个执行器；两个方法均可重写以自定义路由。`onMessage(record, executor)` 接收当前执行回调的执行器，`maxBlockingNum` 控制尚未处理完成的消息积压。
