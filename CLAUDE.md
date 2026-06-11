# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Gradle + Spring Boot 3 的 Java 多模块项目，专注于车辆数据通信协议处理（GB32960、JTT808、Immotors）。使用 Java 25，Spring Boot 3.5.7，Spring Cloud 2025.0.0。

## 模块结构

共 40 个模块（25 个 Lib + 15 个 App），分为两类：

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
- `Lib-Spring-Storage-Mongo`：MongoDB 存储适配（含 Transfer 数据存储）
- `Lib-Spring-Vehicle-Command`：Kafka 请求/响应车辆下行命令框架（`CommandSender`、`CommandReceiver`）
- `Lib-Spring-Monitor-Client`：基于 OSHI 的服务器监控代理（CPU/内存/磁盘/网络指标采集）
- `Lib-Spring-Schedule-Xxljob`：XXL-JOB 分布式任务调度集成
- `Lib-Spring-Prometheus-Exporter`：Prometheus 指标导出端点

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

### Gateway-Tcp 会话集群

`SessionClusterManager` 通过 Kafka `sessionTopic` 处理多网关实例间的会话同步：
- 每个网关实例维护 `ConcurrentHashMap<String, Session>`（VIN → Channel）
- 新 TCP 连接建立时，向 Kafka 发送会话通知（含 VIN + 时间戳）
- 其他实例收到通知后比较时间戳：远端更新则关闭本地旧连接
- 确保同一 VIN 在集群中仅有一个活动 TCP 连接

### Transfer 模块详解

`DataConsumer` 继承 `DataDrivenKafkaConsumer`，每个 Kafka partition 对应一个消费线程，每条消息创建一个 `TransferDataHandler`。TransferDataHandler 内部持有多个 `KafkaDataHandler` 组成的处理链，最终将数据通过 `TcpClient` 发送到第三方平台。

`TcpClient` 核心特性：
- 使用 Netty Bootstrap 建立到第三方平台的 TCP 连接，管理平台登录/登出/心跳（SN 序号通过 Redis 维护）
- **背压**：`ArrayBlockingQueue<SendData>`（容量 100,000），队列满时调用 `dataConsumer.pauseConsume()` 暂停 Kafka 消费
- **重连策略**：前 3 次失败间隔 1 分钟，之后间隔 30 分钟
- 同时启动 Vert.x HTTP 服务器暴露平台登录/登出管理端点

Transfer 支持原始报文转发和解析后数据转发两种模式。

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

## 构建细节

- **无 Gradle Wrapper**：`.gitignore` 显式排除了 `gradlew*`，需系统安装 Gradle
- **Spring Boot 插件来源**：根 `build.gradle` 的 `buildscript` 块通过 version catalog（`libs.spring.boot.gradle.plugin`）解析插件，非本地声明
- **`io.spring.dependency-management` 插件被注释掉**：项目直接使用 platform BOM（`platform(libs.spring.boot.dependencies)`），而非 Spring 依赖管理插件
- **SNAPSHOT 不缓存**：`configurations.configureEach { resolutionStrategy { cacheChangingModulesFor 0, 'seconds' } }` 确保 SNAPSHOT 依赖每次重新解析
- **`afterEvaluate` 时序**：Lib vs App 的 bootJar 开关逻辑在 `afterEvaluate` 中执行；所有子项目统一设置 `springBoot { mainClass = 'cn.bcd.Application' }` 和 `sourceJar` task
- **bootRun JVM 参数**（App 模块）：`-XX:-RestrictContended`、`-Dfile.encoding=UTF-8`、`-Dsun.jnu.encoding=UTF-8`、`--add-opens=java.base/java.nio=ALL-UNNAMED`、`--add-opens=java.base/java.lang=ALL-UNNAMED`、`--add-opens=java.base/java.lang.reflect=ALL-UNNAMED`
- **IDE 支持**：`idea` 插件配置了 `downloadSources = true`、`downloadJavadoc = true`

## 依赖管理

- 所有依赖版本定义在 `gradle/libs.versions.toml`
- 统一排除 logback，使用 log4j2（见根 `build.gradle` 的 `configurations.configureEach`）
- 阿里云 Maven 仓库 + Maven Central
- 所有子项目自动引入 Lombok 和 MapStruct
- **Lib 模块**使用 `api` 传递依赖（`api platform(libs.spring.boot.dependencies)`），**App 模块**使用 `implementation`

## Maven 发布

以下模块配置了 `maven-publish` 插件，发布到 `https://repository.incarcloud.com/content/repositories/snapshots/`：
- `App-BusinessProcess-Backend`：业务后台
- `Lib-Parser-Protocol-GB32960`（`version='1.0-SNAPSHOT'` 覆盖根项目 `version='1.0'`）

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

## 测试

- 使用 **JUnit 6.0.1**（从 JUnit 5 重命名），JUnit Platform，全局配置 `test { useJUnitPlatform() }`
- 无共享基类测试，每个测试类独立编写（`public class` + `@Test` 方法）
- `spring-boot-starter-test` 仅在 `App-BusinessProcess-Backend` 中添加；全局依赖**不含** Mockito、AssertJ、TestContainers
- 当前仅约 13 个测试文件（42 个模块中），多数为手动验证性质的集成测试

## 协议解析框架

`Lib-Parser-Base` 是核心框架，**基于 Javassist 运行时字节码生成**实现高性能二进制协议解析，性能等同于手写解析代码。

### 注解体系

**字段注解（F_*）**：

- `@F_num`：数值字段，支持 `uint8/16/24/32/40/48/56/64`、`int8~int64`、`float32/64` 及枚举类型（枚举需有 `fromInteger(int)` 和 `toInteger()` 方法）。关键参数：
  - `valExpr`：值表达式，使用 `x` 代表原始值（如 `x-10`、`(x+10)*100`）。框架通过 `RpnUtil.reverseExpr()` 自动推导反解析表达式
  - `var`（a-z）：局部变量，缓存解析值供同类的其他字段表达式（`lenExpr`、`valExpr`）引用
  - `globalVar`（A-Z）：全局变量，存入 `ProcessContext.globalVars`（int[26]），贯穿嵌套解析生命周期
  - `checkVal`：值校验，配合 `NumValGetter` 识别异常值（`DefaultNumValGetter`：0xFF/0xFFFF=异常，0xFE/0xFFFE=无效）
  - `precision`：仅 float/double，小数精度四舍五入
- `@F_string`：字符串字段，参数：`len`/`lenExpr`（字节长度）、`charset`（默认 UTF-8）、`appendMode`（补零方向）
- `@F_string_bcd`：BCD 编码字符串（8421 码）
- `@F_num_array`：数值数组，参数：`len`/`lenExpr`（元素个数）、`singleType`、`singleSkip`
- `@F_bean`：嵌套对象。对于接口字段，通过 `implClassExpr` 表达式动态选择 `@C_impl` 标注的实现类
- `@F_bean_list`：嵌套对象列表/数组，支持 `T[]` 和 `List<T>`
- `@F_customize`：自定义 Processor，`processorClass` 实现 `Processor<T>`，`processorArgs` 传递构造参数
- `@F_bit_num`：位域数值（任意位数），参数：`len`（位数）、`unsigned`、`bitRemainingMode`
- `@F_bit_num_easy`：轻量位域解析，用于总位数 ≤32 的连续位组，性能高于 `F_bit_num`。相邻字段形成位组，`end()` 分隔组
- `@F_bit_num_array`：位域数值数组
- `@F_date_ts`：时间戳（模式：uint64_ms/s、uint32_s、float64_ms/s）
- `@F_date_bcd`：6 字节 BCD 日期，参数：`baseYear`、`zoneId`
- `@F_date_bytes_6`：6 字节日期（y/M/d/H/m/s）
- `@F_date_bytes_7`：7 字节日期（2 字节年 + M/d/H/m/s）
- `@F_skip`：字段前后跳过字节（不可单独使用，需与其他 F_ 注解配合）

**类注解（C_*）**：

- `@C_skip(len/lenExpr)`：确保类总字节数匹配指定长度，不足时跳过（parse）或填零（deParse）。`@Inherited`
- `@C_impl(value, processorClass)`：标记接口的实现类，`value` 为选择该实现的表达式值（`Integer.MAX_VALUE` 为默认分支）

### 代码生成流程

`Parser.getProcessor(Class<T>)` 内部流程：
1. 读取类的所有字段注解，按声明顺序排列（父类字段优先）
2. 通过 Javassist `ClassPool` 创建新的 `CtClass`，实现 `Processor<T>`
3. 在 `process(ByteBuf, ProcessContext)` 方法中生成逐字段读取代码，在 `deProcess(ByteBuf, ProcessContext, T)` 中生成逐字段写入代码
4. 每个 `F_*` 注解对应一个 `FieldBuilder` 实现（如 `FieldBuilder__F_num`），负责生成对应类型的解析/反解析 Javassist 代码
5. 编译为字节码 → 实例化 → 缓存到 HashMap（key = `className + byteOrder + numValGetter`）

### 使用方式

数据类定义静态 `Processor` 实例：

```java
public class Packet {
    static final Processor<Packet> processor = Parser.getProcessor(Packet.class);
    // ... 字段 + 注解
}
```

然后通过 `Packet.read(byteBuf)` / `packet.write(byteBuf)` 进行解析/反解析。

### ProcessContext

贯穿解析过程的上下文对象：
- `byteBuf`：当前 ByteBuf
- `parentContext`：父级上下文链（嵌套 bean 可访问父级字段）
- `bitBuf_reader` / `bitBuf_writer`：按需懒创建的位级读写器
- `globalVars`：`int[26]`，A-Z 全局变量，跨嵌套层级传递（如解析头部长度字段后在嵌套 bean 中使用）

### 调试支持

- `Parser.enableGenerateClassFile()`：将生成的 .class 文件写入磁盘
- `Parser.enablePrintBuildLog()`：打印生成的方法体源码到 SLF4J 日志
- `Parser.withDefaultLogCollector_parse/deParse()`：插入日志收集代码（有性能影响）
- `Parser.disableByteBufCheck()`：禁用 Netty ByteBuf 边界检查，提升 10-20% 性能

### 表达式系统

`valExpr`、`lenExpr` 等支持算术表达式（`+`、`-`、`*`、`/`、括号、一元取反 `!`），可引用：
- 局部变量 `var`（a-z）：同类的其他字段
- 全局变量 `globalVar`（A-Z，前缀 `@`）：`ProcessContext` 中的全局变量

`RpnUtil`（逆波兰表达式工具）能将 valExpr 代数反转，自动生成 deParse 代码，避免手动指定逆向表达式。

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

`Lib-Spring-Kafka` 提供两种消费模型：

### DataDrivenKafkaConsumer（数据驱动消费）

基于多线程 Executor + BlockingQueue 的异步消费模型。

**双层线程模型**：
1. **Consumer 线程**（N 个，每个 Kafka partition 一个或每个 topic 一个）：轮询 Kafka，将每条记录按 `id` hash 路由到对应的 `WorkExecutor`，但**不执行业务逻辑**
2. **Worker 线程**（M 个 `WorkExecutor`，每个是 `SingleThreadExecutor` 单线程）：执行 `WorkHandler.init()` / `onMessage()` / `destroy()`

**路由与串行化**：同一 `id`（如 VIN）通过 `hashCode() & (workExecutorNum - 1)` 始终路由到同一个 Worker 线程，保证该 id 的所有消息**串行有序处理**，子类无需加锁。

**WorkHandler 生命周期**（所有方法在同一个 Worker 线程中执行）：
- `newHandler(String id, ConsumerRecord)`：在 Worker 线程中通过 `computeIfAbsent` 懒创建
- `init(ConsumerRecord)`：Handler 创建后立即调用（处理首条消息）
- `onMessage(ConsumerRecord)`：处理后续消息
- `destroy()`：手动删除或 TTL 过期时调用

**背压机制**：`blockingNum` LongAdder 在每次 poll 后递增，`onMessage()` 返回后递减。达到 `maxBlockingNum` 时，Consumer 线程暂停消费（100ms 轮询等待）。

**速率限制**：`maxConsumeSpeed > 0` 时，每秒重置计数器，超过限制则暂停 poll。

**WorkHandler TTL 清理**：可选 `WorkHandlerScanner` 定时销毁超过 `expiredInSecond` 的空闲 Handler。

**监控**：`monitor_log()`（可覆盖）每 `monitor_period` 秒输出一行日志，包含：workExecutor 数量、workHandler 数量、阻塞队列深度（当前/最大）、消费速度（条/秒）、处理速度（条/秒）。

子类需实现：
- `newHandler(String id, ConsumerRecord)`：根据首条消息创建 `WorkHandler`
- `monitor_log()`：返回监控日志字符串

### ThreadDrivenKafkaConsumer（线程驱动消费）

简单的线程内循环消费模型，子类在独立线程中直接实现 `consume(String key, byte[] value)`，用于请求-响应式处理。`Lib-Spring-Vehicle-Command` 中的 `CommandRequestConsumer` 和 `CommandResponseConsumer` 均基于此模型。

## 数据变更通知

`Lib-Spring-Data-Notify` 提供两种通知模式：

### subscribeNotify（点对点订阅通知）

基于 **Kafka + Redis 双通道**设计：

| 用途 | 技术 | 说明 |
|------|------|------|
| 订阅注册（持久化） | Redis Hash | key=`_notify_{type}`，field=订阅者 ID，value=`ListenerInfo` JSON |
| 订阅/取消事件（实时信令） | Kafka topic | `_subscribe_{type}`，消息格式：`'1' + json`（订阅）/ `'2' + id`（取消） |
| 通知投递 | Kafka topic | `_notify_{type}`，key=订阅者 ID |

**心跳与故障恢复**：
- Client 每 **60 秒**将所有订阅的时间戳刷新到 Redis Hash
- Server 每 **60 秒**从 Redis 全量加载，过滤掉时间戳超过 60 秒的过期条目
- Client 崩溃后，其 Redis 条目在 1-2 分钟内自动清除（无需显式调用 `unsubscribe`）
- Server 重启后，在 1 分钟内从 Redis 恢复所有活跃订阅缓存

核心接口：
- `AbstractNotifyServer`：消息提供方实现。`notify(String id, Supplier<byte[]>)` 检查内存缓存中 id 是否订阅，若已订阅则发 Kafka 消息
- `AbstractNotifyClient`：消息消费方实现。`subscribe(String id, Consumer<byte[]>)` / `unsubscribe(String id)` 管理订阅

### onlyNotify（广播通知）

基于 Kafka topic 的简单广播，无订阅管理：
- `Sender<T>`：泛型 Kafka 生产者，序列化为 JSON 字节发送
- `Receiver<T>`：泛型 Kafka 消费者（基于 `ThreadDrivenKafkaConsumer`），反序列化后调用注入的 `Consumer<T>` Bean

内置三种广播类型：`VehicleData`（车辆数据）、`PlatformStatus`（平台在线状态）、`TransferAccess`（转发接入数据）。每种类型的 Sender/Receiver 通过配置属性独立启用。

## 微服务认证

使用 sa-token 做登录态管理，`Lib-Spring-Cloud-Common` 提供 `AuthUser` 和 `UserClient` Feign 接口用于服务间用户信息共享。`App-BusinessProcess-Backend` 提供 `/api/sys/user/getAuthUser` 等认证接口。

## 其他重要组件

- **Vert.x 5.0.11**：`Lib-Websocket`、`App-Simulator-SingleVehicle-Tcp`、`App-DataProcess-Transfer` 使用 Vert.x（非 Spring WebMVC），提供 WebSocket 和 HTTP 服务
- **HiveMQ MQTT 5 Client**：`App-DataProcess-Gateway-Mqtt` 使用 HiveMQ 客户端主动连接外部 MQTT Broker 消费车辆数据
- **XXL-JOB**：`Lib-Spring-Schedule-Xxljob` 集成分布式任务调度（`XxlJobSpringExecutor`）
- **Prometheus**：`Lib-Spring-Prometheus-Exporter` 提供指标导出端点（simpleclient）
- **Nacos**：`Lib-Spring-Data-Init` 通过 Nacos API 进行微服务实例注册/注销
- **OSHI 6.9.0**：`Lib-Spring-Monitor-Client` 使用 OSHI 采集系统指标（CPU/内存/磁盘/网络）
- **Picocli**：独立工具应用（`App-Transponder-GB32960`、`App-Simulator-*`）使用 Picocli 解析命令行参数，非 Spring Boot 应用
- **Bouncy Castle** (`bcprov-jdk18on`)：加密支持
- **EasyExcel 4.0.3**：阿里 Excel 读写
- **无 CI/CD 配置**：仓库不含 GitHub Actions、Jenkinsfile；多数 App 模块有 Dockerfile（基于 `openjdk:21`）
- **无自动化代码风格工具**：无 checkstyle、editorconfig、spotbugs 等配置

## 配置文件机制

每个 App 模块的 `src/main/resources/application.yml` 包含默认配置。`bootJar` 打包时会**排除** `application-local.yml`。开发时可在 jar 同级目录放置 `application-local.yml` 实现增量配置覆盖，或通过 `-Dspring.config.additional-location=app.yml` 指定外部配置文件。`startBootJar.sh` 脚本支持自动检测同目录下的 `app.yml` 并作为增量配置加载。
