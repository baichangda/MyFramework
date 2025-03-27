package cn.bcd.server.business.process.gateway;


import cn.bcd.lib.base.json.JsonUtil;

import java.io.Serial;
import java.io.Serializable;

/**
 * @param <T>
 * @author bcd
 */
public class Result<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

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

    public String getMessage() {
        return message;
    }

    public Result<T> message(String message) {
        this.message = message;
        return this;
    }

    public Result<T> code(int code) {
        this.code = code;
        return this;
    }

    public int getCode() {
        return code;
    }

    public T getData() {
        return data;
    }

    public static <T> Result<T> success() {
        return new Result<>(0, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, data);
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

    public String toJson() {
        return JsonUtil.toJson(this);
    }

}