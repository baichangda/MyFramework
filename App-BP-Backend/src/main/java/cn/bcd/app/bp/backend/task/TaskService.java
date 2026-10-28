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

    /** 仅删除终态任务，保留仍在执行或等待的任务现场。 */
    public void deleteTaskAndDeleteFile(long... ids) {
        List<TaskBean> deleted = deleteFinished(ids);
        String[] paths = deleted.stream().filter(task -> task.result != null && task.result.startsWith("export/"))
                .map(task -> task.result).toArray(String[]::new);
        if (paths.length > 0) {
            fileService.delete(paths);
        }
    }

    /** 普通读取候选 ID；被其他节点抢先处理时调用方立即重新查询。 */
    Long findWaiting(Collection<String> types) {
        String placeholders = String.join(",", Collections.nCopies(types.size(), "?"));
        List<Long> ids = getJdbcTemplate().queryForList(
                "select id from t_sys_task where status=1 and type in (" + placeholders + ") order by id limit 1",
                Long.class, types.toArray());
        return ids.isEmpty() ? null : ids.getFirst();
    }

    boolean claim(long id, String nodeId) {
        return getJdbcTemplate().update("""
                update t_sys_task set status=2, node_id=?, start_time=?
                where id=? and status=1
                """, nodeId, new Date(), id) == 1;
    }

    boolean cancelWaiting(long id) {
        return getJdbcTemplate().update("""
                update t_sys_task set status=5, finish_time=? where id=? and status=1
                """, new Date(), id) == 1;
    }

    /** 不依赖 UPDATE 返回行数，兼容重复停止和驱动的 matched/changed rows 配置。 */
    boolean requestStop(long id) {
        getJdbcTemplate().update("update t_sys_task set stop_requested=true where id=? and status=2", id);
        TaskBean current = get(id);
        return current != null && current.status == TaskStatus.EXECUTING.getStatus() && current.stopRequested;
    }

    boolean isStopRequested(long id) {
        return Boolean.TRUE.equals(getJdbcTemplate().queryForObject(
                "select stop_requested from t_sys_task where id=?", Boolean.class, id));
    }

    void saveProgress(TaskBean task) {
        getJdbcTemplate().update("update t_sys_task set percent=? where id=? and status=2",
                task.percent, task.id);
    }

    /** 终态只能由拥有执行权的 Worker 保存，且只允许从 EXECUTING 转换。 */
    void finish(TaskBean task) {
        getJdbcTemplate().update("""
                update t_sys_task set status=?, percent=?, result=?, message=?, finish_time=?
                where id=? and status=2
                """, task.status, task.percent, task.result, task.message, task.finishTime, task.id);
    }

    /** 取消回调仅保存业务输出，不修改已经确定的取消状态。 */
    void saveCanceled(TaskBean task) {
        getJdbcTemplate().update("""
                update t_sys_task set result=?, message=? where id=? and status=5
                """, task.result, task.message, task.id);
    }

    /** 活跃任务必须先停止，避免删掉其他节点正在使用的记录。 */
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
