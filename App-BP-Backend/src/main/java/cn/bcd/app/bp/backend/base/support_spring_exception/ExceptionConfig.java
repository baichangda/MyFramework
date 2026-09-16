package cn.bcd.app.bp.backend.base.support_spring_exception;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.base.util.ExceptionUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@ControllerAdvice
public class ExceptionConfig {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionConfig.class);

    /**
     * 统一异常错误码
     * <p>
     * 不建议直接使用 HTTP 状态码作为业务码，
     * 因为当前约定是：HTTP 永远返回 200，具体异常通过 Result.code 区分。
     */
    public enum ExceptionCode {
        NOT_LOGIN(10001, "请先登录"),
        ARG_ERROR(10002, "参数错误"),
        RESOURCE_NOT_FOUND(10003, "资源不存在");

        final int code;
        final String msg;

        ExceptionCode(int code, String msg) {
            this.code = code;
            this.msg = msg;
        }
    }

    /**
     * 参数校验异常。
     * <p>
     * 例如：
     *
     * @NotNull
     * @NotBlank
     * @Size
     * @Min 等校验失败后会进入这里。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleMethodArgumentNotValidException(HttpServletResponse response, MethodArgumentNotValidException exception) {
        List<Map<String, String>> errorList = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "msg", fieldError.getDefaultMessage() == null ? "" : fieldError.getDefaultMessage()
                ))
                .toList();
        logger.warn("Request argument validation failed: {}", errorList);
        Result<?> result = Result.fail(ExceptionCode.ARG_ERROR.code, JsonUtil.toJson(errorList));
        writeResponse(response, result);
    }

    /**
     * 静态资源不存在。
     * <p>
     * 比如浏览器自动请求：
     * <p>
     * /favicon.svg
     * /favicon.ico
     * <p>
     * 这类请求不属于系统异常，因此不打印 ERROR。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFoundException(HttpServletResponse response, NoResourceFoundException exception) {
        logger.warn("Resource not found: {}", exception.getResourcePath());
        Result<?> result = Result.fail(ExceptionCode.RESOURCE_NOT_FOUND.code, ExceptionCode.RESOURCE_NOT_FOUND.msg);
        writeResponse(response, result);
    }

    /**
     * 所有未被单独处理的异常最终都会进入这里。
     */
    @ExceptionHandler(Exception.class)
    public void handleException(HttpServletResponse response, Exception exception) {
        if (response.isCommitted()) {
            logger.warn("Response already committed, skip exception handling: {}", exception.getMessage());
            return;
        }
        Throwable realException = ExceptionUtil.getRealException(exception);
        logger.error("Unhandled exception: {}", realException.getMessage(), realException);
        Result<?> result = Result.from(realException);
        writeResponse(response, result);
    }

    /**
     * 统一写入异常响应。
     * <p>
     * 所有异常：
     * <p>
     * HTTP Status = 200
     * <p>
     * 实际成功失败通过 Result.code 判断。
     */
    private void writeResponse(HttpServletResponse response, Result<?> result) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String json = JsonUtil.toJson(result);
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (IOException e) {
            logger.error("Write exception response failed", e);
        }
    }
}