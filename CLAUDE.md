# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Gradle + Spring Boot 3 的 Java 多模块项目，专注于车辆数据通信协议处理（GB32960、JTT808、Immotors）。使用 Java 25，Spring Boot 3.5.7，Spring Cloud 2025.0.0。

## 模块结构

- `Lib-*` 模块：依赖库，不可独立启动，普通 jar 打包
- `App-*` 模块：可部署应用，bootJar 打包

核心 Lib 模块：
- `Lib-Base`：通用工具类（`Result`, `BaseException`, `StringUtil`, `HexUtil` 等）
- `Lib-Parser-Base`：基于注解的二进制协议解析框架，通过 `Parser.getProcessor(Class)` 生成 `Processor<T>` 实现 `process`/`deProcess`
- `Lib-Parser-Protocol-*`：GB32960、JTT808、Immotors 协议实现，协议数据类使用 `@F_num`、`@F_string`、`@F_customize` 等注解标注字段
- `Lib-Spring-*`：Spring 生态扩展（Redis、Kafka、Mongo、JDBC、Database Common 等）
- `Lib-Spring-Cloud-Common`：微服务公共组件（含 Feign 客户端 `UserClient`、统一认证用户 `AuthUser`）
- `Lib-Spring-Database-Common/Jdbc/Mongo`：数据库访问层，提供 `Condition` 条件构造器（`StringCondition`、`NumberCondition`、`DateCondition` 等）支持组合查询

核心 App 模块：
- `App-DataProcess-Gateway-Tcp/Mqtt`：基于 Netty 的数据层网关，接收车辆 TCP/MQTT 数据，通过 Kafka 发送到下游
- `App-DataProcess-Parse`：消费 Kafka 原始数据，调用 `Lib-Parser-Base` 解析协议报文，再发送到 Transfer
- `App-DataProcess-Transfer`：消费解析后的数据，通过 TCP 转发到第三方平台
- `App-BusinessProcess-Backend`：微服务业务后台（Web 服务），使用 sa-token 鉴权，Spring Data JDBC + Mongo 双数据库
- `App-BusinessProcess-Gateway`：微服务网关
- `App-Monitor-Collector`：性能监控服务
- `App-Tool-*`：工具类 Web 服务
- `App-Transponder-GB32960`：GB32960 协议转发器

## 数据流架构

车辆数据从接入到转发的完整链路：

```
Vehicle (TCP) → App-DataProcess-Gateway-Tcp (Netty)
                     ↓
              Kafka (topic: gw-parse)
                     ↓
         App-DataProcess-Parse (解析报文)
                     ↓
              Kafka (topic: 解析后数据)
                     ↓
         App-DataProcess-Transfer (TCP 转发到第三方)
```

Gateway-Tcp 中 `DispatchHandler` 根据报文头字节（`0x2323` = v2016，`0x2424` = v2025）选择对应的 `DataInboundHandler` 和 handler 链。Parse 服务中 `DataConsumer` 同样根据报文字节判断版本，选择对应的 `WorkHandler`。

## 构建命令

Gradle 需预装（无 wrapper）。所有命令在项目根目录执行：

```bash
# 全量构建（跳过测试）
gradle build -x test

# 运行全量测试
gradle test

# 运行单个测试类
gradle :Lib-Parser-Protocol-GB32960:test --tests "cn.bcd.lib.parser.protocol.gb32960.v2016.ParserTest"

# 运行指定模块的测试
gradle :Lib-Parser-Protocol-GB32960:test

# 打包指定应用
gradle :App-DataProcess-Gateway-Tcp:bootJar

# 运行指定应用（开发调试）
gradle :App-DataProcess-Gateway-Tcp:bootRun

# 下载源码和文档（IDE 用）
gradle cleanIdea idea
```

## 依赖管理

- 所有依赖版本定义在 `gradle/libs.versions.toml`
- 统一排除 logback，使用 log4j2（见根 `build.gradle` 的 `configurations.configureEach`）
- 阿里云 Maven 仓库 + Maven Central
- 所有子项目自动引入 Lombok 和 MapStruct
- **Lib 模块**使用 `api` 传递依赖（`api platform(libs.spring.boot.dependencies)`），**App 模块**使用 `implementation`

## 代码规范

- 包名前缀：`cn.bcd`
- 应用类包路径：`cn.bcd.app.{moduleName}`
- 启动类：`cn.bcd.Application.java`，`@SpringBootApplication(scanBasePackages = {"cn.bcd"})`
- 统一异常：`BaseException`（支持 `{}` 占位符格式化，如 `BaseException.get("id={} not found", id)`）
- 统一响应：`Result<T>`（code=0 成功，code>0 失败）
- Web Controller 返回必须使用 `Result`
- 日志使用 log4j2，配置为 `src/main/resources/log4j2.xml`
- 数据库表名以 `t_` 开头，使用自增数字主键
- 配置属性类以 `Prop` 结尾，放在 `prop` 包下
- Web 应用三层结构：`controller` / `service` / `bean`
- Bean 实体以 `Bean` 结尾，需加 Swagger `@Schema` 注解
- Controller 查询接口直接返回 Bean，增改合并为保存接口（通过 id 是否为 null 判断）

## 协议解析框架

`Lib-Parser-Base` 是核心框架，通过字段注解描述二进制报文结构：

- `@F_num`：数字字段，支持 `uint8/16/32/64`、`int8/16/32/64`、`float32/64`
- `@F_string`：字符串字段
- `@F_string_bcd`：BCD 编码字符串
- `@F_num_array`：数字数组
- `@F_date_bcd` / `@F_date_bytes_6` / `@F_date_bytes_7` / `@F_date_ts`：日期字段
- `@F_customize`：自定义类型，指定 `processorClass` 实现 `Processor<T>`
- `@F_bit_num` / `@F_bit_num_easy` / `@F_bit_num_array`：位域数字
- `@F_bean` / `@F_bean_list`：嵌套对象/列表
- `@F_skip` / `@C_skip`：跳过字节

数据类使用静态 `Processor` 实例：`static final Processor<Packet> processor = Parser.getProcessor(Packet.class)`，然后通过 `Packet.read(byteBuf)` / `packet.write(byteBuf)` 进行解析/反解析。

测试参考：`Lib-Parser-Protocol-GB32960/src/test/java/cn/bcd/lib/parser/protocol/gb32960/v2016/ParserTest.java`

## 双版本协议模式

GB32960 协议同时支持 v2016 和 v2025 两个版本，代码组织方式为：
- 数据类：`v2016/data/` 和 `v2025/data/` 分别存放
- Processor：`v2016/processor/` 和 `v2025/processor/` 分别存放
- 应用模块（Gateway/Parse）中通过报文头自动分发到对应版本的 handler 链

## 数据库访问层

`Lib-Spring-Database-Common` 提供统一的查询条件构造：

```java
Condition condition = Condition.and(
    StringCondition.ALL_LIKE("username", username),
    NumberCondition.EQUAL("status", status),
    DateCondition.BETWEEN("createTime", begin, end)
);
```

`Lib-Spring-Database-Jdbc` 基于 Spring Data JDBC，提供通用的 `BaseService`/`BaseDao`；`Lib-Spring-Database-Mongo` 提供 MongoDB 的对应实现。

## Kafka 数据驱动消费

`Lib-Spring-Kafka` 提供 `DataDrivenKafkaConsumer`，一种基于多线程 Executor + BlockingQueue 的数据驱动消费模型，子类需要实现：
- `newHandler(String id, ConsumerRecord)`：根据首条消息创建 WorkHandler
- `monitor_log()`：返回监控日志字符串

## 微服务认证

使用 sa-token 做登录态管理，`Lib-Spring-Cloud-Common` 提供 `AuthUser` 和 `UserClient` Feign 接口用于服务间用户信息共享。`App-BusinessProcess-Backend` 提供 `/api/sys/user/getAuthUser` 等认证接口。
