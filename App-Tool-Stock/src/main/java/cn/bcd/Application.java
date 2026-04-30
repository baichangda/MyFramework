package cn.bcd;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;

public class Application {
    static void main(String[] args) {
        Vertx vertx = Vertx.builder().build();
        WebClient webClient = WebClient.create(vertx);
        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route().path("/test").handler(ctx->{
            String a = ctx.request().getParam("a");
            System.out.println(a);
            ctx.response().send("succeed");
        });
        httpServer.requestHandler(router);
        httpServer.listen(58888).await();
    }
}