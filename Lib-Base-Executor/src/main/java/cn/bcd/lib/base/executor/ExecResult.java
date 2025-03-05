package cn.bcd.lib.base.executor;

public record ExecResult<R>(R result, Throwable throwable) {

    static final ExecResult<Void> void_succeed = new ExecResult<>(null, null);

    public static <R> ExecResult<R> succeed(R r) {
        return new ExecResult<>(r, null);
    }

    public static <R> ExecResult<R> failed(Throwable throwable) {
        return new ExecResult<>(null, throwable);
    }
}
