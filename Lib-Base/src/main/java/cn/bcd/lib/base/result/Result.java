package cn.bcd.lib.base.result;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.ExceptionUtil;
import cn.bcd.lib.base.util.StringUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * @param <T>
 * @author bcd
 */
@Getter
@Setter
public class Result<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * code=0 视为成功
     * 其他情况视为失败
     */
    public int code;
    /**
     * 成功/失败都可能会有信息
     */
    public String message;
    /**
     * 只有成功时候有值、失败则为null
     */
    public T data;

    public Result() {
    }

    private Result(int code, T data) {
        this.code = code;
        this.data = data;
    }

    private Result(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public Result<T> message(String message, Object... args) {
        this.message = format(message, args);
        return this;
    }

    public boolean succeed() {
        return code == 0;
    }

    public static <T> Result<T> success() {
        return new Result<>(0, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, data);
    }

    public static <T> Result<T> successMessage(String message, Object... args) {
        return new Result<>(0, null, format(message, args));
    }

    public static <T> Result<T> fail() {
        return new Result<>(1, null);
    }

    public static <T> Result<T> fail(int code) {
        return new Result<>(code, null);
    }

    public static <T> Result<T> fail(int code, String message, Object... args) {
        return new Result<>(code, null, format(message, args));
    }

    public static <T> Result<T> fail(String message, Object... args) {
        return new Result<>(1, null, format(message, args));
    }

    private static String format(String message, Object... args) {
        if (args.length == 0) {
            return message;
        } else {
            return StringUtil.format(message, args);
        }
    }

    public static Result<?> from(Throwable throwable) {
        Objects.requireNonNull(throwable);
        Throwable realException = ExceptionUtil.getRealException(throwable);
        if (realException instanceof BaseException ex) {
            return Result.fail(ex.code, ex.getMessage());
        } else {
            return Result.fail(realException.getMessage());
        }
    }

}