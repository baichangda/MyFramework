# Lib-Spring-Cloud-Common 使用指南

## 功能

聚合 Spring Cloud LoadBalancer、OpenFeign、Nacos Discovery 和 Log4j2，并提供业务用户服务的 Feign 客户端 `UserClient` 与返回模型 `AuthUser`。

## 引入

```groovy
implementation project(':Lib-Spring-Cloud-Common')
```

在启动类启用服务发现和 Feign 扫描：

```java
@EnableFeignClients(basePackages = "cn.bcd.lib.spring.cloud.common.fegin")
@SpringBootApplication
public class Application { }
```

随后可注入客户端：

```java
private final UserClient userClient;
```

`UserClient` 的服务名固定为 `business-process-backend`，调用前需在 Nacos 中存在同名健康实例，并正确配置 `spring.cloud.nacos.discovery`。包名当前使用 `fegin` 拼写，扫描路径必须与源码保持一致。

```shell
gradle :Lib-Spring-Cloud-Common:build
```
