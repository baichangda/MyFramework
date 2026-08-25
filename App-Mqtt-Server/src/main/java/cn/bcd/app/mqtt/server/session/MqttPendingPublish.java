package cn.bcd.app.mqtt.server.session;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Objects;

/**
 * Broker 向客户端投递、尚未完成确认流程的 QoS 1/2 消息。
 *
 * @param packetId 报文标识符
 * @param message 应用消息
 * @param retained 投递时是否设置保留标志
 * @param sent 当前阶段的报文是否已写入连接
 * @param state 等待的确认状态
 */
public record MqttPendingPublish(
        int packetId,
        MqttApplicationMessage message,
        boolean retained,
        boolean sent,
        MqttOutboundPublishState state
) {

    /**
     * 根据消息 QoS 创建尚未发送的初始待确认状态。
     *
     * @param packetId 报文标识符
     * @param message 应用消息
     * @param retained 是否设置保留标志
     */
    public MqttPendingPublish(
            int packetId,
            MqttApplicationMessage message,
            boolean retained) {
        this(
                packetId,
                message,
                retained,
                false,
                message.qos().value() == 1
                        ? MqttOutboundPublishState.WAIT_PUBACK
                        : MqttOutboundPublishState.WAIT_PUBREC);
    }

    /**
     * 校验待确认消息及状态不能为空。
     *
     * @param packetId 报文标识符
     * @param message 应用消息
     * @param retained 是否设置保留标志
     * @param sent 是否已经发送
     * @param state 确认状态
     */
    public MqttPendingPublish {
        Objects.requireNonNull(message);
        Objects.requireNonNull(state);
    }

    /** 标记当前阶段的报文已经发送，重复调用不会创建新对象。 */
    public MqttPendingPublish asSent() {
        return sent ? this : new MqttPendingPublish(
                packetId, message, retained, true, state);
    }

    /** 将 QoS 2 状态推进到等待 PUBCOMP。 */
    public MqttPendingPublish waitingForPubComp() {
        return state == MqttOutboundPublishState.WAIT_PUBCOMP
                ? this
                : new MqttPendingPublish(
                        packetId,
                        message,
                        retained,
                        sent,
                        MqttOutboundPublishState.WAIT_PUBCOMP);
    }
}
