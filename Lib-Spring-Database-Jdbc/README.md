# Lib-Spring-Database-Jdbc 使用指南

## 功能

提供基于 Spring JDBC 的通用实体服务、分页与条件转换、SQL 工具、MySQL/PostgreSQL 元数据导出、代码生成和 MySQL 定时备份。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Database-Jdbc')
```

使用标准 Spring Boot 数据源配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

实体按现有模型继承 `BaseBean`/`SuperBaseBean`，使用 `@Table` 标记表、`@Transient` 排除非持久化字段。服务可继承 `BaseService<T>`，通过 `Condition` 完成列表、分页和更新操作。

## 工具与安全

代码生成模板位于 `src/main/resources/template`。备份功能只有配置 `lib.spring.database.jdbc.backup.mysql.host` 才启用。数据库密码、备份路径和生成输出目录应由环境配置提供；生成代码后必须人工审查。

`ApplicationYamlUtil.getSpringPropsInYml(...)` 可在 Spring 容器启动前读取 classpath 中的 `application.yml`/`application.yaml` 和 profile 文件。它支持系统属性 `spring.profiles.active`、环境变量 `SPRING_PROFILES_ACTIVE` 以及多个逗号分隔 profile。该工具不实现 Spring Boot 的完整配置优先级，仅适合当前代码生成和数据库元数据工具等轻量场景。
