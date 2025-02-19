package cn.bcd.lib.base.executor;

public final class BlockingChecker {
    public final int periodInSecond;
    public final int expiredInSecond;

    public BlockingChecker(int periodInSecond, int expiredInSecond) {
        this.periodInSecond = periodInSecond;
        this.expiredInSecond = expiredInSecond;
    }

    /**
     * @param periodInSecond  定时任务周期(秒)
     * @param expiredInSecond 判断阻塞时间(秒)
     */
    public static BlockingChecker get(int periodInSecond, int expiredInSecond) {
        return new BlockingChecker(periodInSecond, expiredInSecond);
    }
}