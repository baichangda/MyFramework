# Lib-Spring-Schedule-Xxljob 使用指南

## 功能

根据 Spring 配置创建 XXL-JOB 执行器，并负责向调度中心注册、回调和执行日志管理。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Schedule-Xxljob')
```

```yaml
lib:
  spring:
    schedule:
      xxljob:
        admin-addresses: http://127.0.0.1:8080/xxl-job-admin
        access-token: ${XXL_JOB_TOKEN}
        app-name: demo-executor
        port: 9999
        log-path: ./logs/xxl-job
        log-retention-days: 30
```

配置 `admin-addresses` 后自动创建执行器。任务方法按 XXL-JOB 规范使用 `@XxlJob("handlerName")` 标记，并在调度中心配置相同 Handler 名称。

容器部署时显式设置可被调度中心访问的 `address` 或 `ip`。不要在源码中保存 access token，并确保日志目录可写且有清理策略。
