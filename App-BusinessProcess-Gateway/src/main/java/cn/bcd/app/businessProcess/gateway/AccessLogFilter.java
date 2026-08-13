package cn.bcd.app.businessProcess.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        return chain.filter(exchange).doFinally(signalType -> {
            ServerHttpRequest request = exchange.getRequest();
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            URI targetUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            long costMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            logger.info(
                    "requestId[{}] method[{}] path[{}] route[{}] target[{}] auth[{}] permission[{}] status[{}] signal[{}] costMs[{}]",
                    request.getId(),
                    request.getMethod().name(),
                    request.getPath().value(),
                    Optional.ofNullable(route).map(Route::getUri).map(URI::toString).orElse(""),
                    Optional.ofNullable(targetUrl).map(URI::toString).orElse(""),
                    Boolean.TRUE.equals(exchange.getAttribute(AuthFilter.checkAuth_attr_key)),
                    Boolean.TRUE.equals(exchange.getAttribute(AuthFilter.checkPermission_attr_key)),
                    exchange.getResponse().getStatusCode(),
                    signalType,
                    costMillis
            );
        });
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
