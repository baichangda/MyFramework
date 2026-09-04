package cn.bcd.lib.spring.auth;

import cn.bcd.lib.base.json.JsonUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedUserFilterTest {
    private final AuthenticatedUserFilter filter = new AuthenticatedUserFilter();

    @Test
    void bindsUserOnlyInsideRequestScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthUser user = new AuthUser(7, "alice", "Alice");
        request.addHeader(AuthHeaders.USER, JsonUtil.toJson(user));
        AtomicReference<AuthUser> capturedUser = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                capturedUser.set(AuthenticatedUserContext.current().orElseThrow());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(user, capturedUser.get());
        assertTrue(AuthenticatedUserContext.current().isEmpty());
    }
}
