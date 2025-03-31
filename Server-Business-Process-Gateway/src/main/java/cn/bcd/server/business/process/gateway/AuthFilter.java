package cn.bcd.server.business.process.gateway;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.microservice.common.bean.AuthUser;
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
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    public final static String authUsername_key = "authUser";

    @Autowired
    CacheService cacheService;

    static Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 写入全局上下文 (同步)
        SaReactorSyncHolder.setContext(exchange);
        try {
            SaRouter
                    .match(RouteConfig.pre + "/**")
                    .notMatch(RouteConfig.pre + "/*/v3/api-docs")
                    .notMatch(
                            getExcludeUrls(RouteConfig.business_process_backend_pre,
                                    "/api/anno",
                                    "/api/sys/user/login"
                            )
                    )
                    .check(StpUtil::checkLogin);
            String username = StpUtil.getLoginIdAsString();

            Result<?> failedResult;
            try {
                AuthUser user = cacheService.getUser(username);
                if (user.getStatus() == 0) {
                    failedResult = null;
                } else {
                    failedResult = Result.fail(402, "用户已被禁用");
                }
            } catch (Exception ex) {
                logger.error("error", ex);
                failedResult = Result.fail(500, "登陆校验失败、程序出错");
            }
            if (failedResult == null) {
                // 执行
                exchange.getAttributes().put(AuthFilter.authUsername_key, username);
                return chain.filter(exchange);
            } else {
                String result = JsonUtil.toJson(failedResult);
                exchange.getResponse().getHeaders().set(SaTokenConsts.CONTENT_TYPE_KEY, SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON);
                return exchange.getResponse()
                        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(result.getBytes())));
            }
        } catch (NotLoginException ex) {
            String result = JsonUtil.toJson(Result.fail(401, "请先登陆"));
            exchange.getResponse().getHeaders().set(SaTokenConsts.CONTENT_TYPE_KEY, SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(result.getBytes())));
        } finally {
            SaReactorSyncHolder.clearContext();
        }


    }

    @Override
    public int getOrder() {
        return -100;
    }

    private String[] getExcludeUrls(String pre, String... urls) {
        return Arrays.stream(urls).map(e -> pre + e).toArray(String[]::new);
    }
}
