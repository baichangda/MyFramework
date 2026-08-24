package cn.bcd.app.mqtt.server.authentication;

@FunctionalInterface
public interface MqttAuthenticator {

    boolean authenticate(MqttAuthenticationRequest request);
}
