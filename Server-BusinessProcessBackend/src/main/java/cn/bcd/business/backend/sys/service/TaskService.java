package cn.bcd.business.backend.sys.service;

import cn.bcd.business.backend.base.support_jdbc.service.BaseService;
import cn.bcd.business.backend.base.support_task.TaskDao;
import cn.bcd.business.backend.sys.bean.TaskBean;
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
