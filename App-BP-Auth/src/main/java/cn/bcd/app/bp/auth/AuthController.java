package cn.bcd.app.bp.auth;

import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.auth.AuthHeaders;
import cn.bcd.lib.spring.auth.AuthUser;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<AuthUser> login(@RequestParam String username, @RequestParam String password) {
        AuthUser user = authService.login(username, password);
        return Result.success(user);
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        StpUtil.logout();
        return Result.successMessage("注销成功");
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verify(
            @RequestHeader(value = "X-Forwarded-Uri", required = false) String forwardedUri) {
        if (forwardedUri == null || forwardedUri.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!AuthService.isApiRequest(forwardedUri)) {
            return ResponseEntity.noContent().build();
        }

        StpUtil.checkLogin();
        AuthUser user = authService.currentUser();
        if (!authService.hasPermission(user, forwardedUri)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.noContent()
                .header(AuthHeaders.USER, escapeNonAscii(JsonUtil.toJson(user)))
                .build();
    }

    private static String escapeNonAscii(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch <= 0x7f) {
                result.append(ch);
            } else {
                result.append("\\u").append(String.format("%04x", (int) ch));
            }
        }
        return result.toString();
    }
}
