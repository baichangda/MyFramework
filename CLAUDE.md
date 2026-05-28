# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Gradle + Spring Boot 3 的 Java 多模块项目，专注于车辆数据通信协议处理（GB32960、JTT808、Immotors）。使用 Java 25，Spring Boot 3.5.7，Spring Cloud 2025.0.0。

## 模块结构

- `Lib-*` 模块：依赖库，不可独立启动，普通 jar 打包
- `App-*` 模块：可部署应用，bootJar 打包

核心 Lib 模块：
- `Lib-Base`：通用工具类（`Result`, `BaseException`, `StringUtil`, `HexUtil` 等）
- `Lib-Parser-Base`：基于注解的二进制协议解析框架
- `Lib-Parser-Protocol-*`：GB32960、JTT808、Immotors 协议实现
- `Lib-Spring-*`：Spring 生态扩展（Redis、Kafka、Mongo、JDBC、Database Common 等）
- `Lib-Spring-Cloud-Common`：微服务公共组件

核心 App 模块：
- `App-DataProcess-Gateway-Tcp/Mqtt`：基于 Netty 的数据层网关
- `App-DataProcess-Parse/Transfer`：数据解析与转发服务
- `App-BusinessProcess-Backend`：微服务业务后台（Web 服务）
- `App-BusinessProcess-Gateway`：微服务网关
- `App-Monitor-Collector`：性能监控服务
- `App-Tool-*`：工具类 Web 服务

## 构建命令

Gradle 需预装（无 wrapper）。所有命令在项目根目录执行：

```bash
# 全量构建（跳过测试）
gradle build -x test

# 运行全量测试
gradle test

# 打包指定应用
gradle :App-ModuleName:bootJar

# 运行指定应用（开发调试）
gradle :App-ModuleName:bootRun

# 下载源码和文档（IDE 用）
gradle cleanIdea idea
```

## 依赖管理

- 所有依赖版本定义在 `gradle/libs.versions.toml`
- 统一排除 logback，使用 log4j2（见根 `build.gradle` 的 `configurations.configureEach`）
- 阿里云 Maven 仓库 + Maven Central
- 所有子项目自动引入 Lombok 和 MapStruct

## 代码规范

- 包名前缀：`cn.bcd`
- 应用类包路径：`cn.bcd.app.{moduleName}`
- 启动类：`cn.bcd.Application.java`，`@SpringBootApplication(scanBasePackages = {"cn.bcd"})`
- 统一异常：`BaseException`（支持 `{}` 占位符格式化）
- 统一响应：`Result<T>`（code=0 成功，code>0 失败）
- Web Controller 返回必须使用 `Result`
- 日志使用 log4j2，配置为 `src/main/resources/log4j2.xml`
- 数据库表名以 `t_` 开头，使用自增数字主键
- 配置属性类以 `Prop` 结尾，放在 `prop` 包下
- Web 应用三层结构：`controller` / `service` / `bean`
- Bean 实体以 `Bean` 结尾，需加 Swagger `@Schema` 注解
- Controller 查询接口直接返回 Bean，增改合并为保存接口（通过 id 是否为 null 判断）
