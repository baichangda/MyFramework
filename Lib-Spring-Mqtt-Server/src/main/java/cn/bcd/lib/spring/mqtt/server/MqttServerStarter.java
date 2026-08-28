package cn.bcd.lib.spring.mqtt.server;

import io.moquette.broker.Server;
import io.moquette.broker.config.FluentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

@ConditionalOnProperty(prefix = "lib.spring.mqtt.server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MqttServerProp.class)
@Component
class MqttServerStarter implements CommandLineRunner, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(MqttServerStarter.class);

    private final MqttServerProp mqttServerProp;
    private Server mqttBroker;

    MqttServerStarter(MqttServerProp mqttServerProp) {
        this.mqttServerProp = mqttServerProp;
    }

    @Override
    public void run(String... args) throws Exception {
        if (mqttServerProp.address == null || mqttServerProp.address.isBlank()) {
            throw new IllegalArgumentException("MQTT server address must not be blank");
        }
        if (mqttServerProp.port < 1 || mqttServerProp.port > 65535) {
            throw new IllegalArgumentException("MQTT server port must be between 1 and 65535");
        }

        mqttBroker = new Server();
        FluentConfig config = new FluentConfig(mqttBroker)
                .host(mqttServerProp.address)
                .port(mqttServerProp.port)
                .disableTelemetry();
        if (mqttServerProp.dataPath != null && !mqttServerProp.dataPath.isBlank()) {
            config.dataPath(Paths.get(mqttServerProp.dataPath));
        }
        if (mqttServerProp.persistenceEnabled) {
            config.enablePersistence();
        } else {
            config.disablePersistence();
        }

        config.startServer();
        logger.info("Moquette MQTT server started on {}:{}", mqttServerProp.address, mqttServerProp.port);
    }

    @Override
    public void destroy() {
        if (mqttBroker != null) {
            mqttBroker.stopServer();
            mqttBroker = null;
            logger.info("Moquette MQTT server stopped");
        }
    }
}
