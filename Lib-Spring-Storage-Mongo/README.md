# Lib-Spring-Storage-Mongo 使用指南

## 功能

为时序业务数据提供按分区 ID 哈希分库的 MongoDB 存储。内置 GB32960 原始数据、转发数据和转发响应数据的保存与范围查询。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Storage-Mongo')
```

```yaml
lib:
  spring:
    storage:
      mongo:
        dbs:
          - mongodb://127.0.0.1:27017/vehicle_0
          - mongodb://127.0.0.1:27017/vehicle_1
```

设置 `dbs` 后启用 `MongoUtil`。同一 `partitionId` 始终路由到同一数据库：

```java
MongoUtil_gb32960.save_rawData(rawDataList);
var rows = MongoUtil_gb32960.list_rawData(vin, begin, end, 0, 100, true);
```

分库数量参与哈希路由，已有数据后改变数量会改变定位结果，必须先制定迁移方案。连接 URI 使用外部配置，并为时间范围查询建立匹配索引。
