package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

/** 保留消息的存储与主题过滤器查询接口。 */
public interface MqttRetainedMessageStore {

    /** 新增或覆盖同主题的保留消息。 */
    CompletionStage<Void> save(MqttApplicationMessage message);

    /** 删除指定主题的保留消息。 */
    CompletionStage<Void> delete(String topicName);

    /** 查找主题过滤器匹配的全部保留消息。 */
    Collection<MqttApplicationMessage> findMatching(String topicFilter);
}
