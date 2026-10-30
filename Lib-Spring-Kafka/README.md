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

## 扩展消费者的职责划分

- `ConsumerParam`：构建消费模式、topic/分区和起始位置参数。
- `ConsumerGroup`：拆分参数快照、创建和初始化连接，管理一次启动的消费线程、结果汇总和失败清理。
- `ThreadDrivenKafkaConsumer`：按 ID 路由消息，执行 `onMessage(record, executor)`。
- `DataDrivenKafkaConsumer`：按 ID 维护 `WorkHandler`，负责创建、初始化、消息处理、过期扫描和销毁。

两个 Consumer 各自维护完整的启动、暂停/恢复、限速、拉取、调度和关闭流程，独立实现，便于分别阅读和调整。构造参数及现有调用方式保持兼容。

`startConsume()` 检查状态后，创建并保存 `ConsumerGroup`，再调用 `start()`。`ConsumerGroup` 在各消费线程内创建连接、订阅或指定分区、执行 seek，然后执行具体 Consumer 的 `consume()`。启动协调线程等待全部初始化完成；任意线程启动失败时，先停止并等待本轮全部消费线程退出，再返回失败结果，允许重新启动。关闭和内部线程识别也通过本次 `ConsumerGroup` 完成。

`startConsume(properties)` 返回全部消费者的启动结果，不代表消息处理完成或消费循环结束。默认订阅模式的分区分配在后续 `poll()` 时发生。调用 `.join()` 可等待启动成功并获知启动失败。

关闭顺序为停止消费线程、停止调度任务、排空工作任务、销毁 Handler。工作线程、消费线程和调度线程调用 `close()` 时只发起关闭；外部线程调用时等待关闭完成，重复调用等待同一次关闭。

暂停或达到积压/速度限制时，消费线程仍通过 `poll(100ms)` 维护消费组状态。限速和积压限制按批次检查。offset 提交及业务失败后的重试仍需由使用方明确配置和设计。
