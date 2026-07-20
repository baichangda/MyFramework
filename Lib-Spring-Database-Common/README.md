# Lib-Spring-Database-Common 使用指南

## 功能

定义 JDBC 与 Mongo 公用的查询条件模型：字符串、数字、日期、空值和组合条件。具体数据库模块负责把 `Condition` 转换为 SQL 或 Mongo Criteria。

## 引入与示例

通常通过数据库实现模块传递引入，也可直接声明：

```groovy
implementation project(':Lib-Spring-Database-Common')
```

```java
Condition condition = new ConcatCondition(
        ConcatCondition.ConcatWay.AND,
        List.of(
                StringCondition.ALL_LIKE("name", "demo"),
                NumberCondition.GE("status", 1),
                NullCondition.NOT_NULL("createdAt")
        )
);
```

字段名最终会交给下游转换器处理，只允许来自服务端白名单或固定代码，不要直接接收未经校验的客户端字段名。日期范围可使用 `DateCondition.BETWEEN(...)`。

```shell
gradle :Lib-Spring-Database-Common:build
```
