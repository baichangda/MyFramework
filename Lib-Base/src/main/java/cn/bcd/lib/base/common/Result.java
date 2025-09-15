package cn.bcd.lib.base.common;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.ExceptionUtil;
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

    public int code;
    public String message;
    public T data;

    public Result() {
    }

    public Result(int code, T data) {
        this.code = code;
        this.data = data;
    }

    public Result(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public Result<T> message(String message) {
        this.message = message;
        return this;
    }

    public Result<T> code(int code) {
        this.code = code;
        return this;
    }

    public static <T> Result<T> success() {
        return new Result<>(0, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, data);
    }

    public static <T> Result<T> success_message(String message) {
        return new Result<>(0, null, message);
    }

    public static Result<?> fail() {
        return new Result<>(1, null);
    }

    public static <R> Result<R> fail(int code) {
        return new Result<>(code, null);
    }

    public static <R> Result<R> fail(int code, R data) {
        return new Result<>(code, data);
    }

    public static <R> Result<R> fail(int code, R data, String message) {
        return new Result<>(code, data, message);
    }

    public static <R> Result<R> fail(R data) {
        return new Result<>(1, data);
    }

    public static <R> Result<R> fail_message(String message) {
        return new Result<>(1, null, message);
    }

    public static Result<?> from(Throwable throwable) {
        Objects.requireNonNull(throwable);
        Throwable realException = ExceptionUtil.getRealException(throwable);
        if (realException instanceof BaseException ex) {
            return Result.fail(ex.code).message(ex.getMessage());
        } else {
            return Result.fail_message(realException.getMessage());
        }
    }

}