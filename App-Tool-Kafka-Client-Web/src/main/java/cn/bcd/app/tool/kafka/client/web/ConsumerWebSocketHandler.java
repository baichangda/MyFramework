package cn.bcd.app.tool.kafka.client.web;

import cn.bcd.app.tool.kafka.client.web.base.support_spring_websocket.PathTextWebSocketHandler;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.kafka.KafkaUtil;
import cn.bcd.lib.base.util.HexUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsumerWebSocketHandler extends PathTextWebSocketHandler {

    static final ConcurrentHashMap<WebSocketSession, KafkaConsumer<String, byte[]>> session_consumer = new ConcurrentHashMap<>();

    static Logger logger = LoggerFactory.getLogger(ConsumerWebSocketHandler.class);

    public ConsumerWebSocketHandler() {
        super("/ws/consumer");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.info("handleTextMessage:\n{}", message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("afterConnectionClosed status[{}]", status);
        session_consumer.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("handleTransportError", exception);
        session_consumer.remove(session);
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("afterConnectionEstablished");
        String decode = UriUtils.decode(session.getUri().toString(), StandardCharsets.UTF_8);
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(decode).build();
        MultiValueMap<String, String> queryParams = uriComponents.getQueryParams();

        String[] kafkaAddrs = queryParams.get("kafkaAddrs").getFirst().split(",");
        String kafkaTopic = queryParams.get("kafkaTopic").getFirst();
        String kafkaGroupId = queryParams.get("kafkaGroupId").getFirst();
        boolean isHexData = Boolean.parseBoolean(queryParams.get("isHexData").getFirst());

        KafkaProperties.Consumer consumerProp = new KafkaProperties.Consumer();
        consumerProp.setBootstrapServers(Arrays.asList(kafkaAddrs));
        consumerProp.setGroupId(kafkaGroupId);
        Map<String, Object> prop = consumerProp.buildProperties(new DefaultSslBundleRegistry());
        KafkaConsumer<String, byte[]> consumer = KafkaUtil.newKafkaConsumer_string_bytes(prop);

        session_consumer.put(session, consumer);
        consumer.subscribe(Collections.singletonList(kafkaTopic));

        Thread.ofVirtual().start(() -> {
            try {
                while (session_consumer.containsKey(session)) {
                    ConsumerRecords<String, byte[]> poll;
                    try {
                        poll = consumer.poll(Duration.ofSeconds(1));
                    } catch (Exception ex) {
                        logger.error("error", ex);
                        OutMsg outMsg = new OutMsg();
                        outMsg.succeed = true;
                        outMsg.data = ex.getMessage();
                        outMsg.flag = 101;
                        session.sendMessage(new TextMessage(JsonUtil.toJson(outMsg)));
                        return;
                    }
                    if (poll != null && !poll.isEmpty()) {
                        if (!session.isOpen()) {
                            return;
                        }
                        for (ConsumerRecord<String, byte[]> record : poll) {
                            String str;
                            if (isHexData) {
                                str = HexUtil.hexDump(record.value());
                            } else {
                                str = new String(record.value());
                            }
                            OutMsg outMsg = new OutMsg();
                            outMsg.succeed = true;
                            outMsg.data = str;
                            outMsg.flag = 101;
                            session.sendMessage(new TextMessage(JsonUtil.toJson(outMsg)));
                        }
                    }
                }
            } catch (IOException ex) {
                logger.error("error", ex);
            } finally {
                consumer.close();
                session_consumer.remove(session);
            }
        });

    }
}
