package cn.bcd.app.mqtt.server.authorization;

/**
 * 默认授权器，允许所有发布和订阅操作。
 */
public final class AllowAllMqttAuthorizer implements MqttAuthorizer {

    /**
     * 对任意发布或订阅请求直接授权。
     *
     * @param request 授权请求
     */
    @Override
    public boolean authorize(MqttAuthorizationRequest request) {
        return true;
    }
}
