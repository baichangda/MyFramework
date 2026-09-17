# Lib-Spring-Database-Common 使用指南

## 功能

定义 JDBC 与 Mongo 公用的查询条件模型：字符串、数字、日期、空值和组合条件。具体数据库模块负责把 `Condition` 转换为 SQL 或 Mongo Criteria。

## 引入与示例

通常通过数据库实现模块传递引入，也可直接声明：

```groovy
implementation project(':Lib-Spring-Database-Common')
```

```java
Condition condition = Condition.and(
        StringCondition.ALL_LIKE("name", "demo"),
        NumberCondition.GE("status", 1),
        NullCondition.NOT_NULL("createdAt")
);
```

`Condition.and/or` 会忽略 null；没有有效条件时返回 null，只有一个有效条件时直接返回该条件。条件值和显式传给 `ConcatCondition` 的列表均保持调用方传入的对象。

字段名最终会交给下游转换器处理，只允许来自服务端白名单或固定代码，不要直接接收未经校验的客户端字段名。日期范围可使用 `DateCondition.BETWEEN(...)`，其语义为前闭后开区间。

```shell
gradle :Lib-Spring-Database-Common:test
```
