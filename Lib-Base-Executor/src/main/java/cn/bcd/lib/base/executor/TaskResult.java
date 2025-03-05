package cn.bcd.lib.base.executor;

public record TaskResult<R>(R result, Throwable throwable) {
    public static <R> TaskResult<R> succeed(R r) {
        return new TaskResult<>(r, null);
    }

    public static <R> TaskResult<R> failed(Throwable throwable) {
        return new TaskResult<>(null, throwable);
    }
}
