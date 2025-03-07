package cn.bcd.business.process.gateway;

import cn.bcd.lib.base.json.JsonUtil;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                        RouteConfig.business_process_backend_pre + "/api/anno",
                        RouteConfig.business_process_backend_pre + "/api/sys/user/getVerificationCode",
                        RouteConfig.business_process_backend_pre + "/api/sys/user/login",
                        //tbox上传文件接口
                        RouteConfig.business_process_backend_pre + "/api/third/device/runData",
                        //websocket单独验证token
                        RouteConfig.business_process_backend_pre + "/ws/**",
                        //todo 测试接口，上线后取消
                        RouteConfig.business_process_backend_pre + "/api/sys/user/testLogin"
                )
                .setAuth(obj -> StpUtil.checkLogin())
                .setError(ex -> JsonUtil.toJson(Result.fail(401, "请先登陆")));
    }
}
