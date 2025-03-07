package cn.bcd.server.business.backend.process.sys.task;

import cn.bcd.server.business.backend.process.base.support_task.TaskBuilder;
import cn.bcd.server.business.backend.process.sys.bean.TaskBean;
import cn.bcd.server.business.backend.process.sys.service.TaskService;
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
