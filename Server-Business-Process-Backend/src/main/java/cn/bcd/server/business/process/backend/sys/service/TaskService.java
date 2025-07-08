package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.lib.database.jdbc.service.BaseService;
import cn.bcd.server.business.process.backend.base.support_task.TaskBuilder;
import cn.bcd.server.business.process.backend.base.support_task.TaskDao;
import cn.bcd.server.business.process.backend.sys.bean.TaskBean;
import org.springframework.stereotype.Service;

/**
 *
 */
@Service
public class TaskService extends BaseService<TaskBean> implements TaskDao<TaskBean, Long> {

    public final TaskBuilder<TaskBean, Long> taskBuilder;

    public TaskService() {
        taskBuilder = TaskBuilder.newInstance("common", this, 4);
    }

    @Override
    public TaskBean doCreate(TaskBean task) {
        insert(task);
        return task;
    }

    @Override
    public TaskBean doRead(Long id) {
        return get(id);
    }

    @Override
    public void doUpdate(TaskBean task) {
        update(task);
    }

    @Override
    public void doDelete(TaskBean task) {
        delete(task.getId());
    }

}
