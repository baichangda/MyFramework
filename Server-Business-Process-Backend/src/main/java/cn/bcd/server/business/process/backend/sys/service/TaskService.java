package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.lib.database.jdbc.service.BaseService;
import cn.bcd.server.business.process.backend.base.support_task.TaskBuilder;
import cn.bcd.server.business.process.backend.base.support_task.TaskDao;
import cn.bcd.server.business.process.backend.sys.bean.TaskBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 */
@Service
public class TaskService extends BaseService<TaskBean> implements TaskDao<TaskBean, Long> {

    @Autowired
    FileService fileService;

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

    public void deleteTaskAndDeleteFile(long... ids) {
        List<TaskBean> list = list(ids);
        String[] arr = list.stream().filter(e -> e.type == 1 && e.result != null).map(e -> e.result).toArray(String[]::new);
        delete(ids);
        if (arr.length != 0) {
            fileService.delete(arr);
        }
    }
}
