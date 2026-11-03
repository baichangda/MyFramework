# CRUD 代码生成

每张表默认生成 `Bean`、`QueryRequest`、`SaveRequest`、`Response`、`Service`、`Controller` 六个文件。

- `/list`、`/page` 使用同一个 QueryRequest。`pageNum`、`pageSize` 仍是独立 GET 参数，默认 1、20。
- QueryRequest 默认包含全部非审计字段，日期展开为 `Begin`、`End`；不包含分页参数。
- SaveRequest 默认包含全部非审计业务字段和可空的 `id`。ID 为空新增，否则修改；ID 不参与 SET。
- `createTime/createUserId/createUserName/updateTime/updateUserId/updateUserName` 不进入 Request，由数据库层维护。
- Response 包含全部数据库字段，包括审计信息。
- Service 使用现有 `insert(bean)`、`update(id, map)`；完整编辑表单中，允许为空的字段缺失或为 null 时会清空。
- 字符串查询默认精确匹配；日期范围、数值相等条件由 Service 构建，列表和分页复用。
- 不增加分页参数或批量删除数量校验，不设置列表数量上限，不提供字段级配置。分页直接使用 Spring Data 的 PageRequest。

```java
TableConfig.Helper helper = TableConfig.newHelper();
helper.addModule("User", "用户", "t_sys_user");
Config config = Config.newConfig(targetDir).addTableConfig(helper.toTableConfigs());
CodeGenerator.MYSQL.generate(config);
// CodeGenerator.PGSQL.generate(config);
```

生成后直接编辑 Request、Response 和 Service，删除不需要的字段，调整校验或查询方式。密码等敏感字段也根据业务用途手工删除。
生成时默认覆盖已存在的目标文件。
生成开关分别控制各文件，关闭某类文件生成时，调用方应提供对应的已有类。

生成代码是业务骨架。服务端默认值、权限检查、关联删除等规则需要在 Service 中补充；新增和修改规则明显不同的业务应单独拆分请求。
