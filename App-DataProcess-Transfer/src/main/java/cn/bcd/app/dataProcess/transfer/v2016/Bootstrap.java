package cn.bcd.app.dataProcess.transfer.v2016;

import cn.bcd.app.dataProcess.transfer.v2016.handler.KafkaDataHandler;
import cn.bcd.app.dataProcess.transfer.v2016.tcp.TcpDataHandler;
import cn.bcd.app.dataProcess.transfer.v2016.tcp.TcpClient;
import cn.bcd.lib.base.common.Initializable;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.transferConfig.TransferConfigData;
import cn.bcd.lib.data.init.transferConfig.TransferConfigDataInit;
import cn.bcd.lib.data.notify.onlyNotify.platformStatus.PlatformStatusSender;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class Bootstrap implements ApplicationListener<ContextRefreshedEvent> {

    static Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    @Autowired
    KafkaProperties kafkaProp;

    @Autowired
    List<KafkaDataHandler> kafkaDataHandlers;

    @Autowired
    List<TcpDataHandler> tcpDataHandlers;

    @Value("${transfer.serverId}")
    String serverId;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Autowired
    PlatformStatusSender platformStatusSender;

    HttpServer httpServer;

    @Autowired
    List<Initializable> initList;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            //初始化组件
            Initializable.initByOrder(initList);

            TransferConfigData data = TransferConfigDataInit.get(serverId);
            if (data == null) {
                logger.error("serverId[{}] transfer config not exist", serverId);
                return;
            } else {
                logger.info("load transfer config:\n{}", JsonUtil.toJson(data));
            }

            int[] partitions = Arrays.stream(data.kafkaPartition.split(",")).mapToInt(Integer::parseInt).toArray();
            DataConsumer dataConsumer = new DataConsumer(kafkaProp, "ts-" + data.platCode, partitions, kafkaDataHandlers);
            //初始化tcp客户端
            TcpClient.init(data, dataConsumer, redisTemplate, platformStatusSender,tcpDataHandlers).join();
            //初始化消费者
            dataConsumer.init();
            logger.info("init succeed");

            //启动http服务
            String serverAddress = data.serverAddress;
            Vertx vertx = Vertx.builder().build();
            String[] split = serverAddress.split(":");
            httpServer = vertx.createHttpServer();
            int port = Integer.parseInt(split[1]);
            Router router = Router.router(vertx);
            router.route().handler(LoggerHandler.create(LoggerFormat.SHORT));
            router.route("/api/platformLogin").method(HttpMethod.POST).handler(ctx -> {
                ctx.response().putHeader("content-type", "text/plain");
                Integer res = TcpClient.platformLogin().join();
                logger.info("platformLogin request res[{}]", res);
                ctx.response().send(res.toString());
            });
            router.route("/api/platformLogout").method(HttpMethod.POST).handler(ctx -> {
                ctx.response().putHeader("content-type", "text/plain");
                Integer res = TcpClient.platformLogout().join();
                logger.info("platformLogout request res[{}]", res);
                ctx.response().send(res.toString());
            });
            httpServer.requestHandler(router);
            httpServer.listen(port);
            logger.info("http listen on port[{}]", port);
        } catch (Exception ex) {
            logger.error("error", ex);
        }
    }
}
