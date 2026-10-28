# 集群任务

任务记录和 JSON 参数保存在 `t_sys_task`。每个节点启动固定数量的虚拟线程，空闲时查询
WAITING 任务，通过 `UPDATE ... WHERE status=1` 竞争领取。只有更新成功者可以执行。
不预取、不使用本地等待队列、不自动重试；节点宕机后 EXECUTING 记录保留供人工排查。

## 配置

```yaml
task:
  concurrency: 2
  # 留空消费本节点注册的所有类型，例如 user-export
  types: []
  poll-intervals: [100ms, 250ms, 500ms, 1s, 2s, 5s]
  # node-id 不配置时自动生成，每次进程启动不同
  stop-topic: task-stop
```

所有节点必须连接同一任务数据库，并使用同一个 Redis 广播 topic。
任务表结构维护在 `resources/db/mysql/init.sql` 中。旧版内存任务不能迁移到新版 Worker。

## 增加任务类型

实现 `TaskHandler<P>` 并注册为 Spring Bean，类型字符串唯一，例如 export、report，参数使用可 JSON 序列化的 DTO。
调用 `TaskService.submit(name, type, payload)` 提交。所有消费该类型的节点必须部署对应 Handler。
Handler 是共享 Bean，不保存单次任务的可变字段；单次执行数据放在方法局部变量或 TaskContext。

任务执行、进度更新和开始/终态回调由同一个 Worker 串行调用。通用状态和时间由框架维护；
回调可以设置 task.result、task.message，回调异常只记日志，不改变业务执行结论。
参数无法解析时直接失败，不调用需要该参数的业务回调。

## 停止和状态

状态：WAITING(1) → EXECUTING(2) → SUCCEED(3) / FAILED(4) / STOPPED(6)。
WAITING 也可通过条件更新直接变为 CANCELED(5)。领取提交即为执行边界。

取消方成功将 WAITING 改为 CANCELED 后，在请求线程串行执行取消回调，Worker 不会再领取。
已领取任务先保存 stop_requested，再广播任务 ID。订阅线程只设置本机上下文的 volatile 标记。
Worker 先登记上下文再读取 stop_requested，衔接领取后尚未登记时的停止请求。

业务循环应调用 `context.checkStop()`，响应停止后由原 Worker 保存 STOPPED。
正常完成仍记为 SUCCEED，其他异常记为 FAILED。阻塞 I/O 不会被强行打断。
广播是尽力通知，不补发、不轮询运行任务的停止标记；宕机任务由人工处理。

## 导出

导出任务继承 `ExportHandler<P>` 并注册为 Spring Bean。payload 只包含业务查询参数，
不包含 Service、Condition、排序对象或查询结果。子类在执行阶段提供数据 Service，
将参数转换为 Condition，按需覆盖 sort。抽象类负责分批查询、写 Excel、上传和清理。

例如定义 `record UserQuery(String username) {}`，子类的核心实现为：

```java
@Component
public class UserExportHandler extends ExportHandler<UserQuery> {
    private final UserService userService;

    public UserExportHandler(FileService fileService, UserService userService) {
        super(fileService);
        this.userService = userService;
    }

    public String type() { return "user-export"; }
    public Class<UserQuery> payloadType() { return UserQuery.class; }
    protected BaseService<?> service() { return userService; }

    protected Condition condition(UserQuery query) {
        return StringCondition.EQUAL("username", query.username());
    }

    protected Sort sort(UserQuery query) { return Sort.by("id"); }
}
```

Java 代码统一提交：`taskService.submit("用户导出", "user-export", new UserQuery("Alice"))`。
所有消费节点必须部署对应子类。没有内置的固定导出类型，也没有专门的导出提交入口。
每批读取 1000 条，写入临时 Excel 后上传 S3，结果保存 export/ 下的文件路径，
最终清理临时文件。删除任务时按导出路径识别文件，不依赖具体业务任务类型。

关闭节点时停止领取新任务，等待已领取任务完成后退出，不自动中断任务。

## 测试

任务集成测试读取 application.yml 的 MySQL 和 Redis 配置，不使用内存数据库。
MySQL 测试创建 task_test_ 随机临时库并在结束后删除，连接账号需有建库权限；
Redis 测试使用随机独立 topic，不触碰运行中的任务。
