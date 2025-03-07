package cn.bcd.business.process.gateway;

import cn.bcd.lib.base.json.JsonUtil;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class SaTokenConfig {
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude(
                        RouteConfig.pre + "/**"
                )
                // 业务后台开放地址
                .addExclude(
                        getExcludeUrls(RouteConfig.business_process_backend_pre,
                                "/api/anno",
                                "/api/sys/user/login"
                        )
                )
                .setAuth(obj -> StpUtil.checkLogin())
                .setError(ex -> JsonUtil.toJson(Result.fail(401, "请先登陆")));
    }

    private String[] getExcludeUrls(String pre, String... urls) {
        return Arrays.stream(urls).map(e -> pre + e).toArray(String[]::new);
    }
}
