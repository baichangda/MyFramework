package cn.bcd.lib.spring.auth;

import cn.bcd.lib.base.json.JsonUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AuthenticatedUserFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        AuthUser user = parseUser(request.getHeader(AuthHeaders.USER));
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ScopedValue.where(AuthenticatedUserContext.scopedValue(), user).call(() -> {
                filterChain.doFilter(request, response);
                return null;
            });
        } catch (ServletException | IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServletException(exception);
        }
    }

    private static AuthUser parseUser(String userJson) {
        if (userJson == null || userJson.isBlank()) {
            return null;
        }
        try {
            return JsonUtil.MAPPER.readValue(userJson, AuthUser.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
