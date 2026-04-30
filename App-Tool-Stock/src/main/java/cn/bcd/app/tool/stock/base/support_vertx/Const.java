package cn.bcd.app.tool.stock.base.support_vertx;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.LoggerHandler;

public interface Const {
    Vertx vertx = Vertx.builder().build();
    WebClient webClient = WebClient.create(vertx, new WebClientOptions().setLogActivity(true));
}
