package cn.bcd.lib.base.executor;

public final class BlockingChecker {

    public final static BlockingChecker DEFAULT = BlockingChecker.get(10, 5, 3);

    /**
     * 检查周期
     */
    public final int periodInSecond;
    /**
     * 最大阻塞时间、超过此时间则会打印信息
     */
    public final int maxBlockingTimeInSecond;

    /**
     * 阻塞时打印信息周期
     */
    public final int periodWhenBlockingInSecond;

    public BlockingChecker(int periodInSecond, int maxBlockingTimeInSecond, int periodWhenBlockingInSecond) {
        this.periodInSecond = periodInSecond;
        this.maxBlockingTimeInSecond = maxBlockingTimeInSecond;
        this.periodWhenBlockingInSecond = periodWhenBlockingInSecond;
    }

    /**
     * @param periodInSecond             定时任务周期(秒)
     * @param maxBlockingTimeInSecond    最大阻塞时间、超过此时间则会打印信息
     * @param periodWhenBlockingInSecond 阻塞时打印信息周期
     */
    public static BlockingChecker get(int periodInSecond, int maxBlockingTimeInSecond, int periodWhenBlockingInSecond) {
        return new BlockingChecker(periodInSecond, maxBlockingTimeInSecond, periodWhenBlockingInSecond);
    }

}