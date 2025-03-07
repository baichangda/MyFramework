package cn.bcd.business.process.gateway;

import cn.bcd.lib.base.json.JsonUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaTokenConsts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    public final static String isAuth_key = "isAuth";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 写入全局上下文 (同步)
        SaReactorSyncHolder.setContext(exchange);
        try {
            SaRouter
                    .match(RouteConfig.pre + "/**")
                    .notMatch(
                            getExcludeUrls(RouteConfig.business_process_backend_pre,
                                    "/api/anno",
                                    "/api/sys/user/login"
                            )
                    )
                    .check(StpUtil::checkLogin);
            // 执行
            exchange.getAttributes().put(AuthFilter.isAuth_key, true);
            return chain.filter(exchange);
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
