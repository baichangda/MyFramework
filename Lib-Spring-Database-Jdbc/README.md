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

## SQLite

`SqliteJdbc` 使用 `SQLiteDataSource`、`JdbcTemplate` 和 `JdbcTransactionManager` 提供轻量 SQLite 接入：

```java
try (SqliteJdbc sqlite = SqliteJdbc.open(Path.of("data/app.db"))) {
    JdbcTemplate jdbc = sqlite.jdbcTemplate();
    jdbc.execute("""
            create table if not exists user(
                id integer primary key autoincrement,
                user_name text not null
            )
            """);
    jdbc.update("insert into user(user_name) values (?)", "Alice");

    sqlite.transactionWithoutResult(transactionJdbc -> {
        transactionJdbc.update("update user set user_name=? where id=?", "Bob", 1L);
    });
}
```

默认启用外键约束，并将锁等待时间设为 5 秒。WAL 需要显式开启：

```java
SqliteJdbc sqlite = SqliteJdbc.open(Path.of("data/app.db"), config -> {
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);
    config.setBusyTimeout(10_000);
});
```

自定义回调接收 Xerial 原生 `SQLiteConfig`，可调整其支持的任意 SQLite 连接选项。

`SqliteJdbc.inMemory()` 使用单一物理连接，适合测试和单线程工具，使用完必须关闭。

JDK 25 下运行 SQLite 时可添加 JVM 参数 `--enable-native-access=ALL-UNNAMED`，以允许 Xerial SQLite JDBC 加载本地库。

## 工具与安全

代码生成模板位于 `src/main/resources/template`。备份功能只有配置 `lib.spring.database.jdbc.backup.mysql.host` 才启用。数据库密码、备份路径和生成输出目录应由环境配置提供；生成代码后必须人工审查。

`ApplicationYamlUtil.getSpringPropsInYml(...)` 可在 Spring 容器启动前读取 classpath 中的 `application.yml`/`application.yaml` 和 profile 文件。它支持系统属性 `spring.profiles.active`、环境变量 `SPRING_PROFILES_ACTIVE` 以及多个逗号分隔 profile。该工具不实现 Spring Boot 的完整配置优先级，仅适合当前代码生成和数据库元数据工具等轻量场景。
