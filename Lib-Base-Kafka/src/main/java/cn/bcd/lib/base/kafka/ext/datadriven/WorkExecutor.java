package cn.bcd.lib.base.kafka.ext.datadriven;

import cn.bcd.lib.base.executor.BlockingChecker;
import cn.bcd.lib.base.executor.SingleThreadExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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
     * @param blockingChecker 阻塞检查参数
     * @param doBeforeExit    线程退出前执行的方法
     */
    public WorkExecutor(String threadName, BlockingChecker blockingChecker, Consumer<SingleThreadExecutor> doBeforeExit) {
        super(threadName,
                blockingChecker,
                doBeforeExit);
    }
}
