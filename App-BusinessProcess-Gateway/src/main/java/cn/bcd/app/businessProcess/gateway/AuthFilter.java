package cn.bcd.app.businessProcess.gateway;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.spring.cloud.common.fegin.user.AuthUser;
import cn.bcd.lib.spring.data.init.permission.PermissionDataInit;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaTokenConsts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    public static final String checkAuth_attr_key = "checkAuth";
    public static final String checkPermission_attr_key = "checkPermission";

    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);
    private static final String[] EXCLUDE_URLS = {
            Const.uri_prefix_business_process_backend + "/api/anno",
            Const.uri_prefix_business_process_backend + "/api/sys/user/login"
    };

    private final CacheService cacheService;

    public AuthFilter(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Sa-Token、Redis 和 OpenFeign 均为同步调用，不能占用 WebFlux 的 Netty event-loop。
        return Mono.fromCallable(() -> filterBlocking(exchange, chain))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> result);
    }

    private Mono<Void> filterBlocking(ServerWebExchange exchange, GatewayFilterChain chain) {
        SaReactorSyncHolder.setContext(exchange);
        try {
            if (!requiresAuthentication()) {
                return chain.filter(exchange);
            }

            exchange.getAttributes().put(checkAuth_attr_key, true);
            String username = StpUtil.getLoginIdAsString();
            return filterAuthenticated(exchange, chain, username);
        } catch (NotLoginException ex) {
            return response(exchange, Result.fail(401, "请先登陆"));
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    private Mono<Void> filterAuthenticated(ServerWebExchange exchange, GatewayFilterChain chain, String username) {
        try {
            AuthUser user = cacheService.getUser(username);
            if (user == null) {
                return response(exchange, Result.fail(404, "用户不存在"));
            }
            if (user.getStatus() != 1) {
                return response(exchange, Result.fail(402, "用户已被禁用"));
            }

            ServerWebExchange authenticatedExchange = withAuthUser(exchange, user);
            String requestPath = exchange.getRequest().getPath().value();
            if (!PermissionDataInit.resource_permission.containsKey(requestPath)) {
                return chain.filter(authenticatedExchange);
            }

            exchange.getAttributes().put(checkPermission_attr_key, true);
            if (!StpUtil.hasPermission(requestPath)) {
                return response(exchange, Result.fail(403, "用户权限不足"));
            }
            return chain.filter(authenticatedExchange);
        } catch (Exception ex) {
            logger.error("authentication failed", ex);
            return response(exchange, Result.fail(500, "登陆校验失败、程序出错"));
        }
    }

    private boolean requiresAuthentication() {
        return SaRouter.match(Const.uri_prefix + "/**")
                .notMatch(Const.uri_prefix + "/*/v3/api-docs")
                .notMatch(EXCLUDE_URLS)
                .isHit();
    }

    private ServerWebExchange withAuthUser(ServerWebExchange exchange, AuthUser user) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(Const.request_header_authUser, JsonUtil.toJson(user))
                .build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> response(ServerWebExchange exchange, Result<?> result) {
        byte[] body = JsonUtil.toJson(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().getHeaders().set(
                SaTokenConsts.CONTENT_TYPE_KEY,
                SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON
        );
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body))
        );
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
