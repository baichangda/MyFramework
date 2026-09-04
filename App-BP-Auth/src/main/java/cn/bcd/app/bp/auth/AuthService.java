package cn.bcd.app.bp.auth;

import cn.bcd.lib.spring.auth.AuthUser;
import cn.dev33.satoken.stp.StpUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

@Service
public class AuthService {
    private static final long ADMIN_ID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, List<String>> roleCache;
    private final Cache<String, List<String>> permissionCache;

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(3)).build();
        this.permissionCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(3)).build();
    }

    public AuthUser login(String username, String password) {
        AuthAccount account = findUser(username);
        if (account == null || account.status() != 1 || !passwordEquals(password, account.password())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        StpUtil.login(account.username(), "web");
        return toAuthUser(account);
    }

    public AuthUser currentUser() {
        String username = StpUtil.getLoginIdAsString();
        AuthAccount account = findUser(username);
        if (account == null || account.status() != 1) {
            StpUtil.logout();
            throw new IllegalStateException("用户不存在或已禁用");
        }
        return toAuthUser(account);
    }

    public List<String> roles(String username) {
        return roleCache.get(username, this::loadRoles);
    }

    public List<String> permissions(String username) {
        return permissionCache.get(username, this::loadPermissions);
    }

    public boolean hasPermission(AuthUser user, String requestPath) {
        if (user.id() == ADMIN_ID) {
            return true;
        }
        String apiPath = apiPath(requestPath);
        return permissions(user.username()).stream()
                .anyMatch(permission -> "*".equals(permission) || permission.equals(apiPath));
    }

    public static boolean isApiRequest(String requestUri) {
        return apiPath(requestUri) != null;
    }

    static String apiPath(String requestUri) {
        String path = requestUri == null ? "" : requestUri.split("\\?", 2)[0];
        int index = path.indexOf("/api");
        if (index < 0 || (path.length() > index + 4 && path.charAt(index + 4) != '/')) {
            return null;
        }
        return path.substring(index);
    }

    private AuthAccount findUser(String username) {
        List<AuthAccount> users = jdbcTemplate.query("""
                select id, username, password, real_name, status
                from t_sys_user
                where username = ?
                """, (rs, rowNum) -> new AuthAccount(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("real_name"),
                rs.getInt("status")), username);
        return users.isEmpty() ? null : users.getFirst();
    }

    private List<String> loadRoles(String username) {
        AuthAccount user = findUser(username);
        if (user == null) {
            return List.of();
        }
        if (user.id() == ADMIN_ID) {
            return jdbcTemplate.queryForList("select code from t_sys_role", String.class);
        }
        return jdbcTemplate.queryForList("""
                select distinct r.code
                from t_sys_user_role ur
                inner join t_sys_role r on r.id = ur.role_id
                where ur.user_id = ?
                """, String.class, user.id());
    }

    private List<String> loadPermissions(String username) {
        AuthAccount user = findUser(username);
        if (user == null) {
            return List.of();
        }
        if (user.id() == ADMIN_ID) {
            return jdbcTemplate.queryForList("select resource from t_sys_permission", String.class);
        }
        return jdbcTemplate.queryForList("""
                select distinct p.resource
                from t_sys_user_role ur
                inner join t_sys_role_menu rm on rm.role_id = ur.role_id
                inner join t_sys_menu_permission mp on mp.menu_id = rm.menu_id
                inner join t_sys_permission p on p.id = mp.permission_id
                where ur.user_id = ?
                """, String.class, user.id());
    }

    private static boolean passwordEquals(String submitted, String stored) {
        if (submitted == null || stored == null) {
            return false;
        }
        return MessageDigest.isEqual(
                submitted.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    private AuthUser toAuthUser(AuthAccount account) {
        return new AuthUser(
                account.id(),
                account.username(),
                account.realName());
    }
}
