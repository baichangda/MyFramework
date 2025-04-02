package cn.bcd.server.business.process.gateway;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.permission.PermissionDataInitializer;
import cn.bcd.lib.microservice.common.fegin.user.AuthUser;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaTokenConsts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    public final static String checkAuth_attr_key = "checkAuth";
    public final static String checkPermission_attr_key = "checkPermission";

    @Autowired
    CacheService cacheService;

    static Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 写入全局上下文 (同步)
        SaReactorSyncHolder.setContext(exchange);
        try {
            boolean check = SaRouter
                    .match(Const.uri_prefix + "/**")
                    .notMatch(Const.uri_prefix + "/*/v3/api-docs")
                    .notMatch(
                            getExcludeUrls(Const.uri_prefix_business_process_backend,
                                    "/api/anno",
                                    "/api/sys/user/login"
                            )
                    ).isHit();
            if (check) {
                exchange.getAttributes().put(checkAuth_attr_key, true);
                //登陆校验
                String username = StpUtil.getLoginIdAsString();

                try {
                    //用户状态校验
                    AuthUser user = cacheService.getUser(username);
                    if (user.getStatus() == 1) {
                        ServerHttpRequest newRequest = exchange.getRequest().mutate().header(Const.request_header_authUser, JsonUtil.toJson(user)).build();
                        //判断是否权限校验
                        String requestPath = exchange.getRequest().getPath().value();
                        boolean needCheckPermission = PermissionDataInitializer.resource_permission.containsKey(requestPath);
                        if (needCheckPermission) {
                            exchange.getAttributes().put(checkPermission_attr_key, true);
                            //权限校验
                            boolean hasPermission = StpUtil.hasPermission(requestPath);
                            if (hasPermission) {
                                return chain.filter(exchange.mutate().request(newRequest).build());
                            } else {
                                return response(exchange, Result.fail(403, "用户权限不足"));
                            }
                        }else{
                            return chain.filter(exchange.mutate().request(newRequest).build());
                        }
                    } else {
                        return response(exchange, Result.fail(402, "用户已被禁用"));
                    }
                } catch (Exception ex) {
                    logger.error("error", ex);
                    return response(exchange, Result.fail(500, "登陆校验失败、程序出错"));
                }
            } else {
                return chain.filter(exchange);
            }
        } catch (NotLoginException ex) {
            return response(exchange, Result.fail(401, "请先登陆"));
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    private Mono<Void> response(ServerWebExchange exchange, Result<?> result) {
        String json = JsonUtil.toJson(result);
        exchange.getResponse().getHeaders().set(SaTokenConsts.CONTENT_TYPE_KEY, SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(json.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private String[] getExcludeUrls(String pre, String... urls) {
        return Arrays.stream(urls).map(e -> pre + e).toArray(String[]::new);
    }
}
