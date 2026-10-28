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
 * 进程异常退出后，已领取任务保留 EXECUTING，交由人工排查。
 */
@Component
public class TaskWorker implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(TaskWorker.class);
    private final TaskService taskService;
    private final TaskProperties properties;
    private final Map<String, TaskHandler<?>> handlers;
    private final List<String> types;
    private final ConcurrentHashMap<Long, TaskContext> runningTasks = new ConcurrentHashMap<>();
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

    TaskHandler<?> handler(String type) {
        TaskHandler<?> handler = type == null ? null : handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Unregistered task type: " + type);
        }
        return handler;
    }

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
                    // 竞争失败表示有任务在被处理，立即重新查询，不增加空闲退避。
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

    void requestStop(long id) {
        TaskContext context = runningTasks.get(id);
        if (context != null) {
            context.requestStop();
        }
    }

    void execute(TaskBean task) {
        execute(task, handler(task.type));
    }

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

    private <P> void canceled(TaskBean task, TaskHandler<P> handler) {
        callback(task, () -> handler.onCanceled(new TaskContext(task, taskService),
                JsonUtil.fromJson(task.payload, handler.payloadType())));
        taskService.saveCanceled(task);
    }

    private void failed(TaskBean task, Exception ex) {
        task.status = TaskStatus.FAILED.getStatus();
        task.finishTime = new Date();
        String message = ExceptionUtil.getRealException(ex).getMessage();
        task.message = message == null ? ex.getClass().getSimpleName() : message.substring(0, Math.min(255, message.length()));
        logger.error("Task {} failed", task.id, ex);
    }

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
