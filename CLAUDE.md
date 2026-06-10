# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Gradle + Spring Boot 3 的 Java 多模块项目，专注于车辆数据通信协议处理（GB32960、JTT808、Immotors）。使用 Java 25，Spring Boot 3.5.7，Spring Cloud 2025.0.0。

## 模块结构

共约 40 个模块，分为两类：

- `Lib-*` 模块：依赖库，不可独立启动，普通 jar 打包
- `App-*` 模块：可部署应用，bootJar 打包

核心 Lib 模块：
- `Lib-Base`：通用工具类（`BaseException`、`StringUtil`、`HexUtil` 等）
- `Lib-Base-Json`：`JsonUtil`（基于 Jackson 的 JSON 序列化/反序列化工具）
- `Lib-Base-Executor`：单线程执行器模型（`SingleThreadExecutor`、`ConsumeExecutor` 及其 Group 版本），用于 Netty event loop 外的有序异步处理
- `Lib-Parser-Base`：基于注解的二进制协议解析框架，内部使用 **Javassist 动态字节码生成** `Processor<T>` 实现
- `Lib-Parser-Protocol-*`：GB32960、JTT808、Immotors 协议实现
- `Lib-Jooq`：jOOQ 代码生成模块，为 `App-BusinessProcess-Backend` 提供类型安全 SQL
- `Lib-Websocket`：WebSocket 相关封装
- `Lib-Spring-*`：Spring 生态扩展（Redis、Kafka、Mongo、JDBC、Database Common、Minio、xxl-job、Prometheus 等）
- `Lib-Spring-Cloud-Common`：微服务公共组件（含 Feign 客户端 `UserClient`、统一认证用户 `AuthUser`）
- `Lib-Spring-Database-Common/Jdbc/Mongo`：数据库访问层，提供 `Condition` 条件构造器（`StringCondition`、`NumberCondition`、`DateCondition` 等）支持组合查询
- `Lib-Spring-Data-Init`：数据初始化框架
- `Lib-Spring-Data-Notify`：数据变更通知（发布/订阅），含 `AbstractNotifyServer`/`AbstractNotifyClient` 实现基于 Kafka + Redis 的订阅通知机制
- `Lib-Spring-Storage-Cassandra/Influxdb`：Cassandra 和 InfluxDB 存储适配

核心 App 模块：
- `App-DataProcess-Gateway-Tcp/Mqtt`：基于 Netty 的数据层网关，接收车辆 TCP/MQTT 数据，通过 Kafka 发送到下游
- `App-DataProcess-Parse`：消费 Kafka 原始数据，调用 `Lib-Parser-Base` 解析协议报文，再发送到 Transfer
- `App-DataProcess-Transfer`：消费解析后的数据，通过 TCP 转发到第三方平台
- `App-BusinessProcess-Backend`：微服务业务后台（Web 服务），使用 sa-token 鉴权，Spring Data JDBC + Mongo 双数据库
- `App-BusinessProcess-Gateway`：微服务网关
- `App-Monitor-Collector`：性能监控服务
- `App-Tool-*`：工具类 Web 服务（Minio 客户端、Kafka Web 客户端）
- `App-Transponder-GB32960`：GB32960 协议转发器（TCP 服务端，将接收到的数据转发出去；v2025 支持 SSL）
- `App-Simulator-SingleVehicle-Tcp`：单车模拟器（模拟单台车辆上报数据，带 WebSocket 控制界面）
- `App-Simulator-PressTest-Tcp`：压测模拟器（模拟多车并发上报）

## 模块依赖关系

关键依赖链（修改底层模块会影响所有上层模块）：

```
Lib-Base
  └── Lib-Base-Json / Lib-Base-Executor
  └── Lib-Parser-Base (依赖 Netty + Javassist)
        └── Lib-Parser-Protocol-GB32960 / JTT808 / Immotors
  └── Lib-Spring-* (各 Spring 扩展模块)
        └── App-DataProcess-Gateway-Tcp (依赖 Lib-Spring-Redis/Kafka/Vehicle-Command/Monitor-Client)
        └── App-DataProcess-Parse (依赖 Lib-Parser-Protocol-GB32960 + Lib-Spring-Storage-Mongo/Redis/Kafka)
        └── App-DataProcess-Transfer
        └── App-BusinessProcess-Backend (依赖大量 Lib-Spring-* + Lib-Jooq + sa-token)
        └── ...其他 App 模块
```

**Lib 模块使用 `api` 传递依赖**，因此 Lib-Parser-Base 通过 `api` 传递了 Netty BOM 和 Lib-Base；App 模块使用 `implementation`。

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

### Gateway-Tcp 分发机制

`DispatchHandler` 在 channel 建立后根据报文头字节动态装配 Netty pipeline：
- `0x2323`（`##`）→ v2016：添加 `LengthFieldBasedFrameDecoder` + `DataInboundHandler_v2016`
- `0x2424`（`$$`）→ v2025：添加 `LengthFieldBasedFrameDecoder` + `DataInboundHandler_v2025`

然后 `DataInboundHandler` 会依次调用排序后的 `DataHandler_v2016/v2025` 链（通过 Spring `@Order` 控制顺序），典型 handler 包括：`SessionHandler` → `VehicleOnlineHandler` → `SendKafkaHandler`。

### Parse 模块处理链

`DataConsumer`（基于 `DataDrivenKafkaConsumer`，见下文）为每条 Kafka 消息创建一个 `WorkHandler_v2016/v2025`，其内部依次执行：`DataHandler`（解析报文）→ `SaveRawHandler`（保存原始数据到 Mongo）→ `TransferHandler`（发送到下游 Kafka）。同样通过报文头字节判断版本。

### Transfer 模块消费模型

`DataConsumer` 继承 `DataDrivenKafkaConsumer`，每个 Kafka partition 对应一个消费线程，每条消息创建一个 `TransferDataHandler`。TransferDataHandler 内部持有多个 `KafkaDataHandler` 组成的处理链，最终将数据通过 `TcpClient` 发送到第三方平台。Transfer 支持原始报文转发和解析后数据转发两种模式。

## 构建命令

Gradle 需预装（**无 wrapper**）。所有命令在项目根目录执行：

```bash
# 全量构建（跳过测试）
gradle build -x test

# 清理后全量构建
gradle clean build -x test

# 运行全量测试
gradle test

# 运行指定模块的测试
gradle :Lib-Parser-Protocol-GB32960:test

# 运行单个测试类
gradle :Lib-Parser-Protocol-GB32960:test --tests "cn.bcd.lib.parser.protocol.gb32960.v2016.ParserTest"

# 运行单个测试方法
gradle :Lib-Parser-Protocol-GB32960:test --tests "cn.bcd.lib.parser.protocol.gb32960.v2016.ParserTest.testMethodName"

# 打包指定应用
gradle :App-DataProcess-Gateway-Tcp:bootJar

# 运行指定应用（开发调试）
gradle :App-DataProcess-Gateway-Tcp:bootRun

# 构建指定模块（不运行测试）
gradle :Lib-Parser-Base:build -x test

# 查看模块依赖树
gradle :App-BusinessProcess-Backend:dependencies --configuration runtimeClasspath

# 下载源码和文档（IDE 用）
gradle cleanIdea idea

# 发布 Lib-Backend 到 Maven 仓库（见 build.gradle 中的 publishing 配置）
gradle :App-BusinessProcess-Backend:publishToMavenLocal
```

部署时可用根目录的 `startBootJar.sh` 脚本启动 jar（支持增量配置文件覆盖）。

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

`Lib-Parser-Base` 是核心框架，**基于 Javassist 动态字节码生成**实现高性能解析。通过字段注解描述二进制报文结构：

- `@F_num`：数字字段，支持 `uint8/16/32/64`、`int8/16/32/64`、`float32/64`
- `@F_string`：字符串字段
- `@F_string_bcd`：BCD 编码字符串
- `@F_num_array`：数字数组
- `@F_date_bcd` / `@F_date_bytes_6` / `@F_date_bytes_7` / `@F_date_ts`：日期字段
- `@F_customize`：自定义类型，指定 `processorClass` 实现 `Processor<T>`
- `@F_bit_num` / `@F_bit_num_easy` / `@F_bit_num_array`：位域数字
- `@F_bean` / `@F_bean_list`：嵌套对象/列表
- `@F_skip` / `@C_skip`：跳过字节

数据类使用静态 `Processor` 实例：`static final Processor<Packet> processor = Parser.getProcessor(Packet.class)`，然后通过 `Packet.read(byteBuf)` / `packet.write(byteBuf)` 进行解析/反解析。`Parser.getProcessor()` 内部使用 Javassist 编译生成实现了 `Processor<T>` 接口的字节码类，避免运行时反射带来的性能损耗。

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

`Lib-Jooq` 为 `App-BusinessProcess-Backend` 提供 jOOQ 类型安全 SQL（代码生成自数据库表）。代码生成配置在 `Lib-Jooq/build.gradle` 中，硬编码了本地 MySQL 连接（`jdbc:mysql://127.0.0.1:13306/bcd`）。

## Kafka 数据驱动消费

`Lib-Spring-Kafka` 提供 `DataDrivenKafkaConsumer`，一种基于多线程 Executor + BlockingQueue 的数据驱动消费模型。每个 Kafka partition 分配到一个 `WorkExecutor`（独立线程 + BlockingQueue），子类需要实现：
- `newHandler(String id, ConsumerRecord)`：根据首条消息创建 `WorkHandler`
- `monitor_log()`：返回监控日志字符串

`WorkHandler` 的生命周期由 `DataDrivenKafkaConsumer` 管理：当某个 partition 的第一条消息到达时创建 Handler，后续同 partition 的消息复用该 Handler 顺序处理。

## 数据变更通知

`Lib-Spring-Data-Notify` 提供基于 Kafka + Redis 的发布/订阅机制：
- `AbstractNotifyServer`：由消息提供方实现，注册为 Spring Bean。启动时从 Redis 加载订阅信息，监听 Kafka 上的订阅/取消订阅请求，每隔 1 分钟检查订阅信息变化。通过 `notify(String id, Supplier<byte[]> supplier)` 发送通知。
- `AbstractNotifyClient`：由消息消费方实现，注册为 Spring Bean。通过 `subscribe(String id)`/`unsubscribe(String id)` 管理订阅，监听 Kafka 通知 topic 接收消息。
- 已有通知类型：`VehicleData`（车辆数据）、`PlatformStatus`（平台状态）、`TransferAccess`（转发接入数据）。

## 微服务认证

使用 sa-token 做登录态管理，`Lib-Spring-Cloud-Common` 提供 `AuthUser` 和 `UserClient` Feign 接口用于服务间用户信息共享。`App-BusinessProcess-Backend` 提供 `/api/sys/user/getAuthUser` 等认证接口。

## 配置文件机制

每个 App 模块的 `src/main/resources/application.yml` 包含默认配置。`bootJar` 打包时会**排除** `application-local.yml`。开发时可在 jar 同级目录放置 `application-local.yml` 实现增量配置覆盖，或通过 `-Dspring.config.additional-location=app.yml` 指定外部配置文件。`startBootJar.sh` 脚本支持自动检测同目录下的 `app.yml` 并作为增量配置加载。
