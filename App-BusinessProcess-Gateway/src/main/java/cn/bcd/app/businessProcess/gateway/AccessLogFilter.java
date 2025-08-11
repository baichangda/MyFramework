package cn.bcd.app.businessProcess.gateway;

import cn.bcd.lib.base.common.Const;
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
import java.util.List;
import java.util.Optional;

@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    static Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(
                Mono.fromRunnable(() -> {
                    String authUserStr = Optional.ofNullable(exchange.getRequest().getHeaders().get(Const.request_header_authUser)).map(List::getFirst).orElse(null);
                    boolean checkAuth = Optional.ofNullable(exchange.getAttribute(AuthFilter.checkAuth_attr_key)).map(e -> (boolean) e).orElse(false);
                    boolean checkPermission = Optional.ofNullable(exchange.getAttribute(AuthFilter.checkPermission_attr_key)).map(e -> (boolean) e).orElse(false);
                    ServerHttpRequest request = exchange.getRequest();
                    String sourcePath = request.getURI().toString();
                    String method = request.getMethod().name();
                    String token = Optional.ofNullable(request.getCookies().get("token")).map(e -> {
                        if (e.isEmpty()) {
                            return "";
                        } else {
                            return e.getFirst().getValue();
                        }
                    }).orElse("");
                    Route route = getGatewayRoute(exchange);
                    String lbPath = route.getUri().toString();
                    String targetPath = Optional.ofNullable(getTargetUrl(exchange)).map(URI::toString).orElse("");
                    logger.info("-------------request start-------------");
                    logger.info("sourcePath: {}", sourcePath);
                    logger.info("lbPath: {}", lbPath);
                    logger.info("targetPath: {}", targetPath);
                    logger.info("method: {}", method);
                    logger.info("token: {}", token);
                    logger.info("{}: {}", AuthFilter.checkAuth_attr_key, checkAuth);
                    logger.info("{}: {}", Const.request_header_authUser, authUserStr);
                    logger.info("{}: {}", AuthFilter.checkPermission_attr_key, checkPermission);
                    logger.info("responseStatus: {}", exchange.getResponse().getStatusCode());
                    logger.info("-------------request end-------------");
                })
        );
    }

    private Route getGatewayRoute(ServerWebExchange exchange) {
        return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    }

    private URI getTargetUrl(ServerWebExchange exchange) {
        return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
    }


    @Override
    public int getOrder() {
        return -200;
    }
}
