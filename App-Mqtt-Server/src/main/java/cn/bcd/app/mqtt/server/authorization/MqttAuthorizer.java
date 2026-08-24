package cn.bcd.app.mqtt.server.authorization;

@FunctionalInterface
public interface MqttAuthorizer {

    boolean authorize(MqttAuthorizationRequest request);
}
