package cn.bcd.app.bp.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    @Test
    void detectsApiPathAndRemovesQueryString() {
        assertTrue(AuthService.isApiRequest("/backend/api/user/list?page=1"));
        assertEquals("/api/user/list", AuthService.apiPath("/backend/api/user/list?page=1"));
        assertEquals("/api/login", AuthService.apiPath("/auth/api/login"));
        assertEquals("/api/user/list", AuthService.apiPath("/api/user/list"));
    }

    @Test
    void doesNotTreatSimilarPrefixAsApi() {
        assertFalse(AuthService.isApiRequest("/api-docs"));
        assertNull(AuthService.apiPath("/auth/login"));
    }
}
