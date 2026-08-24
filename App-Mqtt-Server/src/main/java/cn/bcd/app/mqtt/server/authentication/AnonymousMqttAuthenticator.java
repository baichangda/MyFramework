package cn.bcd.app.mqtt.server.authentication;

public final class AnonymousMqttAuthenticator implements MqttAuthenticator {

    @Override
    public boolean authenticate(MqttAuthenticationRequest request) {
        return true;
    }
}
