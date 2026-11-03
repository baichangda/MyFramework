# Mongo CRUD 代码生成

保留已有 Bean，生成 `QueryRequest`、`SaveRequest`、`QueryResponse`、`Service`、`Controller` 五个文件，默认覆盖同名文件。

```java
CollectionConfig config = new CollectionConfig("Vehicle", "车辆", VehicleBean.class);
CodeGenerator.generate(config);

// 也可以显式指定输出目录，用于预览或 Bean 不位于本地源码目录的情况。
CollectionConfig preview = new CollectionConfig("Vehicle", "车辆", VehicleBean.class, targetDir);
CodeGenerator.generate(preview);
```

CollectionConfig 只提供模块、类型、目录和文件生成开关，不提供字段配置或数量上限。
三个新增开关是 `needCreateQueryRequestFile`、`needCreateSaveRequestFile`、`needCreateQueryResponseFile`。

## 模型与接口

- Bean、BaseBean、SuperBaseBean 不使用 `@Schema` 和校验注解；保留 Mongo 映射注解与字段注释。
- 生成器通过反射读取字段，不依赖 Bean 上的文档或校验注解。描述默认使用字段名，生成后人工完善。
- 常用的 java.util、java.math、java.time 类型通过 import 使用简写，java.lang 类型省略包名；自定义类型保留完整名称。
- 静态字段、Java transient 字段、Spring `@Transient` 字段不进入接口模型。
- SaveRequest 保留可写的非审计业务字段和可空 String ID；复杂对象、集合、Map、数组保留完整泛型类型。
- QueryResponse 保留持久化字段，包括审计信息；通过静态 `fromBean(bean)` 转换。映射按字段或公开 getter 读取。
- QueryRequest 的 `toCondition()` 为字符串、字符、布尔、数字、日期字段构建筛选，日期展开为 Begin/End；复杂字段查询按业务补充。
- QueryRequest 和 SaveRequest 均排除创建、更新审计字段。基本类型转为包装类型，以区分未提交与零值、false。
- Mongo 没有统一的表字段约束，生成器不推断请求必填、长度等校验，生成后按业务添加。

`/list`、`/page` 共用 QueryRequest，返回 QueryResponse。分页参数独立接收，默认 1、20，不增加范围校验或数量上限。
`/save` 接收 SaveRequest；`/delete` 接收 String ID 数组，不限制数量。

## 保存与字段映射

- ID 为 null 时构造新 Bean，调用现有 `BaseService.save(bean)`，由原逻辑维护创建信息。未提交的基本类型字段使用 Bean 的默认值。
- ID 非 null 时生成明确的 `Update.set()`，调用实体感知的 `MongoTemplate.updateFirst(query, update, Bean.class)`，不使用 upsert 或整个文档替换。
- 修改时可空编辑字段缺失或为 null 表示写入 null，不自动转为 `$unset`；需要必填的字段应在 SaveRequest 中添加校验。
- 主键、创建信息和只读字段不参与更新。更新信息按 `DocumentExt.autoSetUpdateInfo` 配置维护，操作人通过现有 UserGetter 获取。
- 使用 matchedCount 判断目标是否存在，修改值相同不会误报记录不存在。
- 条件和更新使用 Java 属性名，实体感知的 MongoTemplate 调用负责 `_id` 和 `@Field` 等映射。

生成器不修改原 Bean，也不新增 BaseService 方法。服务端默认值、数据权限、字段取舍和关联删除规则在生成后人工补充。
