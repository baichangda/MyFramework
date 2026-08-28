# Lib-Base-Executor 使用指南

## 功能

提供按业务 ID 固定分片的单线程执行器 `IdEventExecutorGroup`，以及批量消费相关的 `ConsumeExecutor`、`ConsumeExecutorGroup`。相同 ID 的任务会落到同一执行器串行处理，不同 ID 可并行执行。

## 引入

```groovy
implementation project(':Lib-Base-Executor')
```

## 快速使用

```java
try (IdEventExecutorGroup group = new IdEventExecutorGroup(4)) {
    group.getEventExecutor("vehicle-001").execute(() -> handle("vehicle-001"));
}
```

`IdEventExecutorGroup` 只负责根据 ID 分配执行器，不提供无 ID 的任务提交接口。线程数必须大于 0，实际数量会向上取整为 2 的幂。业务 ID 不能为 `null`；同一 ID 内不要执行长时间阻塞任务，否则会阻塞该分片后续工作。使用完毕后调用 `close()` 释放线程。

## 验证

```shell
gradle :Lib-Base-Executor:test
```
