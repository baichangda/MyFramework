package cn.bcd.app.bp.auth;

import cn.bcd.lib.base.result.Result;
import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<?>> notLogin(NotLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401).message("请先登录"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> badCredentials(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401).message(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<?>> unavailableUser(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401).message(exception.getMessage()));
    }
}
