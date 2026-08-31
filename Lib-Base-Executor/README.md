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
    group.execute("vehicle-001", () -> handle("vehicle-001"));
    group.schedule("vehicle-001", () -> handle("vehicle-001"), 1, TimeUnit.SECONDS);
}
```

`IdEventExecutorGroup` 提供带业务 ID 的 `execute`、`submit`、`schedule`、`scheduleAtFixedRate` 和 `scheduleWithFixedDelay` 方法。`submit` 和 `schedule` 同时支持 `Runnable` 和 `Callable`，其余方法接收 `Runnable`。业务 ID 用于将任务路由到固定的单线程执行器。也可以通过 `getEventExecutor` 直接获取 ID 对应的执行器。线程数必须大于 0，实际数量会向上取整为 2 的幂。业务 ID 不能为 `null`；同一 ID 内不要执行长时间阻塞任务，否则会阻塞该分片后续工作。使用完毕后调用 `close()` 释放全部线程。

## 验证

```shell
gradle :Lib-Base-Executor:test
```
