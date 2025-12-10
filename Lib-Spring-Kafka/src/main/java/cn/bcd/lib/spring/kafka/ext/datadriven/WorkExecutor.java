package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.executor.SingleThreadExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作执行器
 * 注意:
 * 非阻塞任务线程中的任务不能阻塞、且任务之间是串行执行的、没有线程安全问题
 */
public class WorkExecutor extends SingleThreadExecutor {
    /**
     * 存储本执行器所有的handler
     */
    public final Map<String, WorkHandler> workHandlers = new HashMap<>();

    /**
     * 构造任务执行器
     *
     * @param threadName      线程名称
     * @param schedule        是否开启计划任务
     */
    public WorkExecutor(String threadName, boolean schedule) {
        super(threadName, 0, schedule);
    }
}
