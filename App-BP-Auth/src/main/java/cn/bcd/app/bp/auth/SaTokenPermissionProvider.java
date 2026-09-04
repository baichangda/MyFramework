package cn.bcd.app.bp.auth;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaTokenPermissionProvider implements StpInterface {
    private final AuthService authService;

    public SaTokenPermissionProvider(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return authService.permissions(loginId.toString());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return authService.roles(loginId.toString());
    }
}
