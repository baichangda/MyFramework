package cn.bcd.app.businessProcess.gateway;

import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.spring.cloud.common.fegin.user.AuthUser;
import cn.bcd.lib.spring.cloud.common.fegin.user.UserClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CacheServiceTest {

    @Test
    void cachesUsersWithinTheConfiguredTtl() {
        StubUserClient client = new StubUserClient();
        CacheService cacheService = new CacheService(client);

        AuthUser first = cacheService.getUser("alice");
        AuthUser second = cacheService.getUser("alice");

        assertSame(first, second);
        assertEquals(1, client.userCalls.get());
    }

    @Test
    void usesStructuredRoleAndPermissionCacheKeys() {
        StubUserClient client = new StubUserClient();
        CacheService cacheService = new CacheService(client);

        assertEquals(List.of("a,b:c"), cacheService.getRoleList("a,b", "c"));
        assertEquals(List.of("a:b,c"), cacheService.getRoleList("a", "b,c"));
        assertEquals(List.of("a,b:c"), cacheService.getPermissionList("a,b", "c"));
        assertEquals(List.of("a:b,c"), cacheService.getPermissionList("a", "b,c"));
        assertEquals(2, client.roleCalls.get());
        assertEquals(2, client.permissionCalls.get());
    }

    private static class StubUserClient implements UserClient {

        private final AtomicInteger userCalls = new AtomicInteger();
        private final AtomicInteger roleCalls = new AtomicInteger();
        private final AtomicInteger permissionCalls = new AtomicInteger();

        @Override
        public Result<List<String>> getUserRoles(String username, String loginType) {
            roleCalls.incrementAndGet();
            return Result.success(List.of(username + ":" + loginType));
        }

        @Override
        public Result<List<String>> getUserPermissions(String username, String loginType) {
            permissionCalls.incrementAndGet();
            return Result.success(List.of(username + ":" + loginType));
        }

        @Override
        public Result<AuthUser> getAuthUser(String username) {
            userCalls.incrementAndGet();
            return Result.success(new AuthUser(1, username, 1));
        }
    }
}
