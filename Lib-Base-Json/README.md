# Lib-Base-Json 使用指南

## 功能

封装项目统一的 Jackson `JsonMapper`。`JsonUtil` 默认支持常用日期类型、宽松反序列化和统一 JSON 输出，适合在模块间保持序列化行为一致。

## 引入

```groovy
implementation project(':Lib-Base-Json')
```

## 快速使用

```java
String json = JsonUtil.toJson(value);
String pretty = JsonUtil.toJsonPretty(value);
byte[] bytes = JsonUtil.toJsonAsBytes(value);

Vehicle vehicle = JsonUtil.OBJECT_MAPPER.readValue(json, Vehicle.class);
```

复杂泛型可通过 `JsonUtil.getJavaType(type)` 构造 Jackson 类型。需要定制 Mapper 时使用 `JsonUtil.buildObjectMapper()` 获取独立实例，不要修改全局 `OBJECT_MAPPER`，以免影响其他模块。

## 验证

```shell
gradle :Lib-Base-Json:test
```
