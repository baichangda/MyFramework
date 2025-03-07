package cn.bcd.lib.microservice.common.result;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.util.ExceptionUtil;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "Api调用编码(0:成功;1:失败;其他代表各种业务定义的错误)")
    public int code;
    @Schema(description = "Api调用返回的提示信息")
    public String message;
    @Schema(description = "Api调用返回的数据")
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

    public static Result<?> from(Throwable throwable) {
        Objects.requireNonNull(throwable);
        Throwable realException = ExceptionUtil.getRealException(throwable);
        if (realException instanceof BaseException ex) {
            return Result.fail(ex.code).message(ex.getMessage());
        } else {
            return Result.fail().message(realException.getMessage());
        }
    }

}