package cn.bcd.app.tool.stock.base.support_vertx;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxUtil {
    static Logger logger= LoggerFactory.getLogger(VertxUtil.class);
    public static final Vertx vertx = Vertx.builder().build();
    public static final WebClient webClient = WebClient.create(vertx);
}
