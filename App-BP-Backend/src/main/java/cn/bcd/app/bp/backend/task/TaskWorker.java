package cn.bcd.app.bp.backend.task;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 每个长期运行的虚拟线程自行领取并完整处理一个任务，不预取、不使用本地等待队列。
 * 执行线程数就是节点总并发上限，各任务类型共享；执行和所有收尾完成后才再次领取。
 * 进程异常退出后，已领取任务保留 EXECUTING，交由人工排查，不自动重试或恢复。
 */
@Component
public class TaskWorker implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(TaskWorker.class);
    private final TaskService taskService;
    private final TaskProperties properties;
    /** 启动时构建且不可变；Handler 单例会同时处理不同任务。 */
    private final Map<String, TaskHandler<?>> handlers;
    /** 本节点允许领取的类型，配置留空时取全部已注册类型。 */
    private final List<String> types;
    /** 仅保存已领取且尚未完成收尾的任务，供停止请求线程查找。 */
    private final ConcurrentHashMap<Long, TaskContext> runningTasks = new ConcurrentHashMap<>();
    /** 生命周期线程写，所有轮询线程读；关闭时不再进入下一轮领取。 */
    private volatile boolean running;
    private ExecutorService executor;

    // 领取服务同时提供提交和停止入口，延迟注入以避免启动时循环依赖。
    public TaskWorker(@Lazy TaskService taskService, TaskProperties properties, List<TaskHandler<?>> handlers) {
        properties.validate();
        this.taskService = taskService;
        this.properties = properties;
        Map<String, TaskHandler<?>> registered = new HashMap<>();
        for (TaskHandler<?> handler : handlers) {
            String type = handler.type();
            if (type == null || type.isBlank() || type.length() > 64) {
                throw new IllegalArgumentException("Task type must contain 1-64 characters");
            }
            if (registered.putIfAbsent(type, handler) != null) {
                throw new IllegalArgumentException("Duplicate task type: " + handler.type());
            }
        }
        this.handlers = Map.copyOf(registered);
        this.types = properties.getTypes().isEmpty()
                ? registered.keySet().stream().sorted().toList() : List.copyOf(properties.getTypes());
        for (String type : types) {
            handler(type);
        }
    }

    /** 按类型查找处理器，提交和执行共用；未部署的类型直接拒绝。 */
    TaskHandler<?> handler(String type) {
        TaskHandler<?> handler = type == null ? null : handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unregistered task type: " + type);
        }
        return handler;
    }

    /**
     * Spring 上下文就绪后启动固定数量的长期 Worker，而不是为每个任务另开线程。
     * 未注册 Handler 时不启动扫描；重复启动不会重复增加并发。
     */
    @Override
    public synchronized void start() {
        if (running || types.isEmpty()) {
            return;
        }
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("task-worker-", 0).factory());
        running = true;
        for (int i = 0; i < properties.getConcurrency(); i++) {
            executor.submit(this::poll);
        }
    }

    /**
     * 关闭领取循环并等待已有任务及回调收尾，不发停止请求、不调用线程 interrupt。
     * 阻塞业务可能延长关闭时间，任务应自行设置合理的 I/O 超时。
     */
    @Override
    public synchronized void stop() {
        running = false;
        if (executor != null) {
            // 不打断正在执行的业务。Worker 完成当前任务后退出，空闲线程最多等待一个轮询间隔。
            executor.close();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 每个 Worker 独立维护空闲阶梯；领取成功立即重置，竞争失败立即重查。
     * 无候选或基础设施异常时退避，附加约 10% 内的抖动，分散多节点扫描时间。
     */
    private void poll() {
        int idleStep = 0;
        while (running) {
            try {
                Long id = taskService.findWaiting(types);
                if (id != null) {
                    if (taskService.claim(id, properties.getNodeId())) {
                        idleStep = 0;
                        execute(taskService.get(id));
                    }
                    // 候选已被领取或取消，立即重新查询，不增加空闲退避。
                    continue;
                }
            } catch (Exception ex) {
                logger.error("Task polling/execution error on node {}", properties.getNodeId(), ex);
            }
            try {
                long millis = properties.getPollIntervals().get(idleStep).toMillis();
                long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, millis / 10));
                Thread.sleep(millis + jitter);
                idleStep = Math.min(idleStep + 1, properties.getPollIntervals().size() - 1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 由请求线程或 Redis 订阅线程调用，只置位本机标记；不移除映射或更新数据库。 */
    void requestStop(long id) {
        TaskContext context = runningTasks.get(id);
        if (context != null) {
            context.requestStop();
        }
    }

    /** 仅处理已领取的任务，调用方必须已通过 claim 取得执行权。 */
    void execute(TaskBean task) {
        execute(task, handler(task.type));
    }

    /**
     * 整个生命周期在当前 Worker 内串行推进。终态回调完成后才保存结果并移除运行映射。
     * 业务正常返回优先认定成功；只有停止异常走 STOPPED，其他 Exception 走 FAILED。
     */
    private <P> void execute(TaskBean task, TaskHandler<P> handler) {
        TaskContext context = new TaskContext(task, taskService);
        runningTasks.put(task.id, context);
        try {
            P payload;
            try {
                payload = JsonUtil.fromJson(task.payload, handler.payloadType());
            } catch (Exception ex) {
                // 参数损坏时不能向业务回调传入无效参数，直接记录失败。
                failed(task, ex);
                return;
            }
            try {
                // 必须先登记再读取：登记前的停止由数据库承接，登记后的停止由广播承接。
                if (taskService.isStopRequested(task.id)) {
                    context.requestStop();
                }
                context.checkStop();
                callback(task, () -> handler.onStarted(context, payload));
                context.checkStop();
                task.result = handler.execute(context, payload);
                task.status = TaskStatus.SUCCEED.getStatus();
                task.percent = 100F;
                task.finishTime = new Date();
                callback(task, () -> handler.onSucceeded(context, payload));
            } catch (TaskStopException ex) {
                task.status = TaskStatus.STOPPED.getStatus();
                task.finishTime = new Date();
                callback(task, () -> handler.onStopped(context, payload));
            } catch (Exception ex) {
                failed(task, ex);
                callback(task, () -> handler.onFailed(context, payload, ex));
            }
        } finally {
            try {
                // 即使参数解析失败也保存 FAILED；保存异常交给轮询层记录，不再次执行该任务。
                taskService.finish(task);
            } finally {
                runningTasks.remove(task.id, context);
            }
        }
    }

    /** cancelWaiting 成功后调用；此时没有 Worker 能领取该任务，取消方独占回调执行权。 */
    void canceled(TaskBean task) {
        canceled(task, handler(task.type));
    }

    /** 在取消请求线程中解析参数并回调，最后仅保存 result/message，不再次转换状态。 */
    private <P> void canceled(TaskBean task, TaskHandler<P> handler) {
        callback(task, () -> handler.onCanceled(new TaskContext(task, taskService),
                JsonUtil.fromJson(task.payload, handler.payloadType())));
        taskService.saveCanceled(task);
    }

    /** 在内存中整理失败状态，截断错误信息以适配数据库列，稍后统一保存。 */
    private void failed(TaskBean task, Exception ex) {
        task.status = TaskStatus.FAILED.getStatus();
        task.finishTime = new Date();
        String message = ExceptionUtil.getRealException(ex).getMessage();
        task.message = message == null ? ex.getClass().getSimpleName() : message.substring(0, Math.min(255, message.length()));
        logger.error("Task {} failed", task.id, ex);
    }

    /** 同步调用且隔离回调异常，避免回调失败覆盖业务执行的最终结论。 */
    private void callback(TaskBean task, Callback callback) {
        try {
            callback.run();
        } catch (Exception ex) {
            logger.error("Task {} lifecycle callback failed", task.id, ex);
        }
    }

    @FunctionalInterface
    private interface Callback {
        void run() throws Exception;
    }
}
