package cn.bcd.lib.spring.kafka.ext.datadriven;

import cn.bcd.lib.base.executor.BlockingChecker;
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
     * @param blockingChecker 阻塞检查周期任务的执行周期(秒)
     *                        如果<=0则不启动阻塞检查
     *                        开启后会启动周期任务
     *                        检查逻辑为
     *                        向执行器中提交一个空任务、等待{@link BlockingChecker#maxBlockingTimeInSecond}秒后检查任务是否完成
     *                        如果没有完成则警告、且此后每{@link BlockingChecker#periodWhenBlockingInSecond}秒检查一次任务情况并警告
     */
    public WorkExecutor(String threadName, boolean schedule, BlockingChecker blockingChecker) {
        super(threadName,
                0,
                schedule,
                blockingChecker
        );
    }
}
