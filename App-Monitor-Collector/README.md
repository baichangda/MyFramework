# App-Monitor-Collector

## 部署

1. 在 MySQL 中执行 `src/main/resources/db/mysql/init.sql`，已有表的部署无需调整表结构。
2. 配置 `spring.datasource` 和 `spring.data.redis`。
3. 在 `t_server_data` 中登记服务，server_id 与客户端 server-id 完全一致。
4. 与客户端一起升级。同一个监控域只运行一个 Collector；多个监控域应分别使用不同的 request-topic、response-list 和数据库。
5. 启动：`gradle :App-Monitor-Collector:bootRun`。

```yaml
spring:
  main:
    keep-alive: true
lib:
  spring:
    monitor:
      client:
        request-topic: monitorRequest
        response-list: monitorResponse
        collect-cron: "0/15 * * * * *"
```

Collector 已启用 Spring 调度，默认每十五秒发起一次采集，等待十秒响应。collect-cron 属于采集端配置，客户端无需设置。开启虚拟线程时 keep-alive 使非 Web 采集服务持续运行。

## 结果处理

仅接受当前批次、非空 serverId 且带有 systemData 的响应。重复服务响应保留最新有效项；名单中缺少有效响应的服务保存 status=1，正常响应保存 status=0。迟到响应只会进入原批次队列，不能将下一批服务错误标为正常。

每批读取并清理独立的响应队列后，直接批量入库，不保存待入库快照，也不重试旧批次。入库失败时允许丢失本批结果，下次调度继续发起新批次。迟到产生的残留队列一天后过期。

## 验证

```text
gradle :Lib-Spring-Monitor-Client:test :App-Monitor-Collector:test --offline
```

测试覆盖响应身份、序列化、扩展同步调用与异常隔离、批次隔离、重复响应、故障状态、直接入库和后续批次继续采集，以及仓库配置下的调度注册。Redis 和 JDBC 使用测试替身，未连接实际中间件。
