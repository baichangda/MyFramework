# Lib-Jooq 使用指南

## 功能

提供 jOOQ 与 MySQL 驱动，并保存 `bcd` 数据库生成的表、键和 Record 类型。生成代码位于 `cn.bcd.lib.jooq.generate`，可配合 Spring 注入的 `DSLContext` 编写类型安全 SQL。

## 引入与查询

```groovy
implementation project(':Lib-Jooq')
```

```java
import static cn.bcd.lib.jooq.generate.Tables.T_SYS_USER;

var users = dsl.selectFrom(T_SYS_USER)
        .where(T_SYS_USER.STATUS.eq(1))
        .fetch();
```

## 重新生成模型

`build.gradle` 中的 jOOQ 配置当前包含本地 MySQL 地址、账号和固定输出目录。运行生成任务前，先改为当前环境的安全配置和仓库内输出路径，再执行：

```shell
gradle :Lib-Jooq:jooqCodegen
```

不要提交真实数据库密码。数据库结构变更后应重新生成，并检查生成代码差异。
