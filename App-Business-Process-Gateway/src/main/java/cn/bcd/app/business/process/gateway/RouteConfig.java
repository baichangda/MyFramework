package cn.bcd.app.business.process.gateway;

import cn.bcd.lib.base.common.Const;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    private GatewayFilterSpec rewritePath(GatewayFilterSpec spec, String pathPre) {
        return spec.rewritePath(pathPre + "/(?<segment>.*)", "/${segment}");
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(r -> r.path(Const.uri_prefix_business_process_backend + "/**")
                        .filters(e -> rewritePath(e, Const.uri_prefix_business_process_backend))
                        .uri("lb://business-process-backend"))
                .build();
    }
}
