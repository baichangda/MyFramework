package cn.bcd.lib.spring.mqtt.server;

import com.hivemq.embedded.EmbeddedHiveMQ;
import com.hivemq.embedded.EmbeddedHiveMQBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ConditionalOnProperty(prefix = "lib.spring.mqtt.server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MqttServerProp.class)
@Component
class MqttServerStarter implements CommandLineRunner, DisposableBean {

    static Logger logger = LoggerFactory.getLogger(MqttServerStarter.class);

    final MqttServerProp mqttServerProp;
    EmbeddedHiveMQ hiveMQ;
    Path configurationFolder;

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

        configurationFolder = Files.createTempDirectory("hivemq-config-");
        Files.writeString(
                configurationFolder.resolve("config.xml"),
                createConfig(mqttServerProp.address, mqttServerProp.port),
                StandardCharsets.UTF_8);

        EmbeddedHiveMQBuilder builder = EmbeddedHiveMQ.builder()
                .withoutLoggingBootstrap()
                .withConfigurationFolder(configurationFolder);
        if (mqttServerProp.dataPath != null && !mqttServerProp.dataPath.isBlank()) {
            builder.withDataFolder(Paths.get(mqttServerProp.dataPath));
        }

        hiveMQ = builder.build();
        hiveMQ.start().join();
        logger.info("HiveMQ MQTT server started on {}:{}", mqttServerProp.address, mqttServerProp.port);
    }

    @Override
    public void destroy() throws Exception {
        try {
            if (hiveMQ != null) {
                hiveMQ.close();
            }
        } finally {
            if (configurationFolder != null) {
                Files.deleteIfExists(configurationFolder.resolve("config.xml"));
                Files.deleteIfExists(configurationFolder);
            }
        }
    }

    private static String createConfig(String address, int port) {
        return """
                <?xml version="1.0"?>
                <hivemq>
                    <listeners>
                        <tcp-listener>
                            <port>%d</port>
                            <bind-address>%s</bind-address>
                        </tcp-listener>
                    </listeners>
                </hivemq>
                """.formatted(port, escapeXml(address));
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
