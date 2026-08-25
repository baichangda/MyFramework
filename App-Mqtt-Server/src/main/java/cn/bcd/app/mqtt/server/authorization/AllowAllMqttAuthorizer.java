package cn.bcd.app.mqtt.server.authorization;

/**
 * 默认授权器，允许所有发布和订阅操作。
 */
public final class AllowAllMqttAuthorizer implements MqttAuthorizer {

    @Override
    public boolean authorize(MqttAuthorizationRequest request) {
        return true;
    }
}
