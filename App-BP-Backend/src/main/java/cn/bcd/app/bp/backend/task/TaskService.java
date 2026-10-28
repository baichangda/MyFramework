package cn.bcd.app.bp.backend.task;

import cn.bcd.app.bp.backend.file.FileService;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 集中提供任务提交、领取、停止及状态保存。领取和取消用条件 UPDATE 竞争处理权。
 * 执行数据更新只写指定字段，不覆盖停止请求；不使用覆盖整个业务过程的长事务。
 * 提交方只持久化参数，执行方再查询业务数据；创建时间和创建人复用 BaseService.insert 填充。
 */
@Service
public class TaskService extends BaseService<TaskBean> {
    private final TaskWorker worker;
    private final TaskStopSubscriber stopSubscriber;
    private final FileService fileService;

    public TaskService(TaskWorker worker, TaskStopSubscriber stopSubscriber, FileService fileService) {
        this.worker = worker;
        this.stopSubscriber = stopSubscriber;
        this.fileService = fileService;
    }

    /**
     * 保存 WAITING 记录并返回 ID，不在请求线程执行任务或提前查询导出数据。
     * 存在调用方事务时随其提交，Worker 只能领取已经提交的记录。
     *
     * @param name 展示名称，长度 1 到 50
     * @param type 本节点已注册的 Handler 类型，消费节点也必须部署对应 Handler
     * @param payload 仅包含业务参数、可 JSON 序列化为对应 DTO 的对象
     * @return 新建任务 ID，不表示任务已经开始执行
     */
    public long submit(String name, String type, Object payload) {
        if (name == null || name.isBlank() || name.length() > 50 || payload == null) {
            throw new IllegalArgumentException("Task name (1-50 characters) and payload are required");
        }
        TaskHandler<?> handler = worker.handler(type);
        String json = JsonUtil.toJson(payload);
        // 提交时检查参数能否被对应 Handler 解析，避免把明显错误的参数交给集群。
        JsonUtil.fromJson(json, handler.payloadType());
        TaskBean task = new TaskBean(name, type);
        task.payload = json;
        task.status = TaskStatus.WAITING.getStatus();
        insert(task);
        return task.id;
    }

    /**
     * 暂停调用方事务，确保停止意图已经提交再广播。
     * 取消与领取只竞争 WAITING 状态；已领取任务的终态只能由原 Worker 保存。
     * 等待任务取消后在当前请求线程执行取消回调；执行中任务只保存停止意图并广播通知。
     *
     * @param ids 要处理的任务 ID
     * @return 与输入顺序一一对应的处理结果；STOP_REQUESTED 不代表业务已经退出
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StopResult[] stop(Long... ids) {
        StopResult[] results = new StopResult[ids.length];
        for (int i = 0; i < ids.length; i++) {
            long id = ids[i];
            // 先读参数再尝试取消，避免取消落库后记录被终态清理操作删除而无法执行回调。
            TaskBean task = get(id);
            if (task == null) {
                results[i] = StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND;
                continue;
            }
            if (cancelWaiting(id)) {
                task.status = TaskStatus.CANCELED.getStatus();
                task.finishTime = new Date();
                worker.canceled(task);
                results[i] = StopResult.CANCEL_SUCCEED;
            } else if (requestStop(id)) {
                // 本节点立即置位，集群中的其他节点通过广播接收同一请求。
                worker.requestStop(id);
                stopSubscriber.send(id);
                results[i] = StopResult.STOP_REQUESTED;
            } else {
                results[i] = StopResult.WAIT_OR_IN_EXECUTING_NOT_FOUND;
            }
        }
        return results;
    }

    /**
     * 先删除终态记录，再清理其 export/ 路径下的文件，支持子类自定义的导出类型。
     * 文件清理不与数据库删除组成事务，外部文件服务异常会向调用方传播。
     */
    public void deleteTaskAndDeleteFile(long... ids) {
        List<TaskBean> deleted = deleteFinished(ids);
        String[] paths = deleted.stream().filter(task -> task.result != null && task.result.startsWith("export/"))
                .map(task -> task.result).toArray(String[]::new);
        if (paths.length > 0) {
            fileService.delete(paths);
        }
    }

    /**
     * 按 ID 读取一个候选，不申请行锁，也不代表已取得执行权。
     * types 由 Worker 保证非空；返回 null 才表示本次扫描没有候选。
     * 多节点可能读到同一 ID，必须继续调用 claim，以更新结果判断归属。
     */
    Long findWaiting(Collection<String> types) {
        String placeholders = String.join(",", Collections.nCopies(types.size(), "?"));
        List<Long> ids = getJdbcTemplate().queryForList(
                "select id from t_sys_task where status=1 and type in (" + placeholders + ") order by id limit 1",
                Long.class, types.toArray());
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /**
     * Worker 在自动提交模式下竞争 WAITING → EXECUTING，同时记录领取节点和开始时间。
     * 返回 true 才能执行；返回 false 表示已经被领取或取消，应重新扫描。
     */
    boolean claim(long id, String nodeId) {
        return getJdbcTemplate().update("""
                update t_sys_task set status=2, node_id=?, start_time=?
                where id=? and status=1
                """, nodeId, new Date(), id) == 1;
    }

    /**
     * 与 claim 竞争同一个旧状态。更新成功即取得取消处理权，任务不再可被领取。
     * 返回 false 时不能根据先前查询的状态判断结果，应继续尝试处理执行中的停止请求。
     */
    boolean cancelWaiting(long id) {
        return getJdbcTemplate().update("""
                update t_sys_task set status=5, finish_time=? where id=? and status=1
                """, new Date(), id) == 1;
    }

    /**
     * 仅记录停止意图，保留 EXECUTING。重复请求仍允许再次广播。
     * 不依赖 UPDATE 行数，兼容驱动的 matched/changed rows 差异；
     * 随后若发现任务已结束，则不再通知执行节点。
     */
    boolean requestStop(long id) {
        getJdbcTemplate().update("update t_sys_task set stop_requested=true where id=? and status=2", id);
        TaskBean current = get(id);
        return current != null && current.status == TaskStatus.EXECUTING.getStatus() && current.stopRequested;
    }

    /** 领取后、登记本机上下文后读取一次，用于衔接登记前到达的停止请求。 */
    boolean isStopRequested(long id) {
        return Boolean.TRUE.equals(getJdbcTemplate().queryForObject(
                "select stop_requested from t_sys_task where id=?", Boolean.class, id));
    }

    /** 由任务所属线程保存进度，不改状态、节点归属或外部写入的停止意图。 */
    void saveProgress(TaskBean task) {
        getJdbcTemplate().update("update t_sys_task set percent=? where id=? and status=2",
                task.percent, task.id);
    }

    /**
     * 由原 Worker 在终态回调后保存执行结果，只允许从 EXECUTING 转换。
     * 使用明确字段更新，避免旧实体覆盖 stop_requested；保存失败时不自动重跑任务。
     */
    void finish(TaskBean task) {
        getJdbcTemplate().update("""
                update t_sys_task set status=?, percent=?, result=?, message=?, finish_time=?
                where id=? and status=2
                """, task.status, task.percent, task.result, task.message, task.finishTime, task.id);
    }

    /** 取消请求线程在回调后仅保存业务输出，不修改已经落库的 CANCELED 状态。 */
    void saveCanceled(TaskBean task) {
        getJdbcTemplate().update("""
                update t_sys_task set result=?, message=? where id=? and status=5
                """, task.result, task.message, task.id);
    }

    /**
     * 条件删除保证等待或执行中的记录不会被移除；返回实际删除的记录供调用方清理结果文件。
     * 对不存在或非终态的 ID 直接跳过，不负责停止任务。
     */
    public List<TaskBean> deleteFinished(long... ids) {
        List<TaskBean> deleted = new ArrayList<>();
        for (TaskBean task : list(ids)) {
            if (getJdbcTemplate().update("delete from t_sys_task where id=? and status in (3,4,5,6)", task.id) == 1) {
                deleted.add(task);
            }
        }
        return deleted;
    }
}
