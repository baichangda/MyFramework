package cn.bcd.business.process.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {
    public final static String pre = "/service";
    public final static String business_process_backend_pre = pre + "/backend";


    private GatewayFilterSpec rewritePath(GatewayFilterSpec spec, String pathPre) {
        return spec.rewritePath(pathPre + "/(?<segment>.*)", "/${segment}");
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(r -> r.path(business_process_backend_pre + "/**")
                        .filters(e -> rewritePath(e, business_process_backend_pre))
                        .uri("lb://business-process-backend"))
                .build();
    }
}
