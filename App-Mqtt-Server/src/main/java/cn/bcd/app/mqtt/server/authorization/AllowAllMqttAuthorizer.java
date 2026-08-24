package cn.bcd.app.mqtt.server.authorization;

public final class AllowAllMqttAuthorizer implements MqttAuthorizer {

    @Override
    public boolean authorize(MqttAuthorizationRequest request) {
        return true;
    }
}
