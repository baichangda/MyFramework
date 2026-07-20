# Lib-Spring-Database-Mongo 使用指南

## 功能

提供 Spring Data MongoDB 通用 CRUD 服务、条件转换、唯一字段处理、动态 Mongo 连接和代码生成。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Database-Mongo')
```

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://127.0.0.1:27017/demo
```

领域对象使用 `@DocumentExt`，按需继承 `BaseBean`/`SuperBaseBean`，唯一字段使用 `@Unique`。建立服务时继承 `BaseService<T>` 并确保 Bean 位于 Spring 扫描范围；可调用 `list`、`page`、`get`、`save`、`delete` 和 `updateMulti`。

```java
List<Device> devices = deviceService.list(
        StringCondition.EQUAL("vin", vin));
```

动态连接通过 `DynamicMongoUtil` 管理。不要用未经校验的请求字段构造条件。生成模板位于 `src/main/resources/template`，生成结果需人工审查。
