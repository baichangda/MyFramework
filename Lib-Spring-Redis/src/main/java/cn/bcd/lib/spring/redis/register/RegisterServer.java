package cn.bcd.lib.spring.redis.register;

public enum RegisterServer {
    test1(5),
    test2(10);

    /**
     * 服务提供者宕机后，消费者感知到下线的最大时间。
     */
    public final int maxTimeout_s;
    public final int provider_heartbeat_s;
    public final long consumer_localCacheExpired_ms;
    public final long consumer_providerInfoExpired_ms;

    RegisterServer(int maxTimeout_s) {
        if (maxTimeout_s < 2) {
            throw new IllegalArgumentException("maxTimeout_s must be greater than or equal to 2");
        }
        this.maxTimeout_s = maxTimeout_s;
        int consumerLocalCacheExpired = (maxTimeout_s - 1) / 2;
        consumer_localCacheExpired_ms = consumerLocalCacheExpired * 1000L;
        provider_heartbeat_s = (maxTimeout_s - 1) - consumerLocalCacheExpired;
        consumer_providerInfoExpired_ms = (provider_heartbeat_s + 1) * 1000L;
    }
}
