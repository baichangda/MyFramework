# Lib-Spring-Storage-Cassandra 使用指南

## 功能

创建 DataStax `CqlSession`，并提供 GB32960 原始报文的异步保存、单条查询和基于 paging state 的分页查询。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Storage-Cassandra')
```

```yaml
lib:
  spring:
    storage:
      cassandra:
        dbs:
          - 127.0.0.1:9042/datacenter1/keyspace1
        username: ${CASSANDRA_USER}
        password: ${CASSANDRA_PASSWORD}
```

设置 `dbs` 后创建 `CqlSession`。原始数据可使用 `CassandraUtil_gb32960.save_rawData(list)`；分页调用返回 `CompletableFuture<PageResult<RawData>>`，下一页应传回上次结果的 paging state。

所有异步异常都应由调用方处理。投入使用前核对 keyspace、表结构和 `dbs` 字符串格式是否与 `CassandraConfig` 一致，凭据使用外部密钥配置。
