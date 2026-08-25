package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.session.MqttInboundQosTwoPublish;
import cn.bcd.app.mqtt.server.session.MqttPendingPublish;
import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;
import cn.bcd.app.mqtt.server.session.MqttSubscription;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

/**
 * 持久会话存储接口。
 *
 * <p>启动加载为同步操作；运行期间的变更返回异步阶段，调用方应等待其完成后再向客户端
 * 确认，以保证协议状态与持久化状态一致。</p>
 */
public interface MqttSessionStore {

    /** 加载全部持久会话快照。 */
    Collection<MqttSessionSnapshot> loadAll();

    /** 新建会话或更新会话元数据。 */
    CompletionStage<Void> upsertSession(
            String clientId,
            String username,
            int nextPacketId);

    /** 删除会话及其关联的订阅和飞行中消息。 */
    CompletionStage<Void> deleteSession(String clientId);

    /** 新增订阅或更新已有过滤器的 QoS。 */
    CompletionStage<Void> upsertSubscription(
            String clientId,
            MqttSubscription subscription);

    /** 批量删除会话中的订阅。 */
    CompletionStage<Void> deleteSubscriptions(
            String clientId,
            Collection<String> topicFilters);

    /** 保存出站待确认消息，并同步更新下一个报文标识符。 */
    CompletionStage<Void> upsertPendingPublish(
            String clientId,
            int nextPacketId,
            MqttPendingPublish pending);

    /** 删除已完成确认的出站消息。 */
    CompletionStage<Void> deletePendingPublish(String clientId, int packetId);

    /** 保存等待 PUBREL 的 QoS 2 入站消息。 */
    CompletionStage<Void> upsertInboundQosTwo(
            String clientId,
            MqttInboundQosTwoPublish publish);

    /** 删除已经通过 PUBREL 释放的 QoS 2 入站消息。 */
    CompletionStage<Void> deleteInboundQosTwo(String clientId, int packetId);
}
