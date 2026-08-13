package cn.bcd.app.businessProcess.gateway;

import cn.bcd.lib.base.common.Const;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("business-process-backend", route -> route
                        .path(Const.uri_prefix_business_process_backend + "/**")
                        .filters(filters -> filters.stripPrefix(2))
                        .uri("lb://business-process-backend"))
                .build();
    }
}
