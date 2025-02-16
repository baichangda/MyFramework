package cn.bcd.server.businessProcess.backend.sys.task;

import cn.bcd.server.businessProcess.backend.base.support_task.TaskBuilder;
import cn.bcd.server.businessProcess.backend.sys.bean.TaskBean;
import cn.bcd.server.businessProcess.backend.sys.service.TaskService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskConfig {

    TaskService taskService;

    @Bean
    public TaskBuilder<TaskBean,Long> taskBuilder() {
        return TaskBuilder.newInstance("common", taskService,1);
    }
}
