package cn.bcd.server.business.backend.process.sys.service;

import cn.bcd.server.business.backend.process.base.support_jdbc.service.BaseService;
import cn.bcd.server.business.backend.process.base.support_task.TaskDao;
import cn.bcd.server.business.backend.process.sys.bean.TaskBean;
import org.springframework.stereotype.Service;

/**
 *
 */
@Service
public class TaskService extends BaseService<TaskBean> implements TaskDao<TaskBean, Long> {
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
