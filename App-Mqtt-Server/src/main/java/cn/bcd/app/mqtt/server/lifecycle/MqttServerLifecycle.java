package cn.bcd.app.mqtt.server.lifecycle;

import cn.bcd.app.mqtt.server.config.MqttServerProperties;
import cn.bcd.app.mqtt.server.netty.MqttServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** 将 MQTT 服务的启停接入 Spring 容器生命周期。 */
@Component
public class MqttServerLifecycle implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MqttServerLifecycle.class);

    private final MqttServerProperties properties;
    private final MqttServer mqttServer;

    /**
     * 创建 Spring 生命周期适配器。
     *
     * @param properties 服务配置
     * @param mqttServer MQTT 网络服务
     */
    public MqttServerLifecycle(MqttServerProperties properties, MqttServer mqttServer) {
        this.properties = properties;
        this.mqttServer = mqttServer;
    }

    /** 启动 MQTT 监听端口并记录实际绑定地址。 */
    @Override
    public void start() {
        mqttServer.start();
        logger.info("MQTT server started, bindAddress[{}] port[{}]",
                properties.getBindAddress(), mqttServer.getBoundPort());
    }

    /** 停止 MQTT 服务并记录停止日志。 */
    @Override
    public void stop() {
        mqttServer.stop();
        logger.info("MQTT server stopped");
    }

    @Override
    public boolean isRunning() {
        return mqttServer.isRunning();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isEnabled();
    }

    public int getBoundPort() {
        return mqttServer.getBoundPort();
    }
}
