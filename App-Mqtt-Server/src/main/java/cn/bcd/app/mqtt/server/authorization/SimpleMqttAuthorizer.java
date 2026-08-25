package cn.bcd.app.mqtt.server.authorization;

import cn.bcd.app.mqtt.server.config.MqttAuthorizationProperties;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;
import cn.bcd.lib.base.exception.BaseException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于配置规则的发布、订阅授权器。
 *
 * <p>身份条件为空时表示通配。发布规则按“过滤器是否匹配主题名”判断，订阅规则则要求
 * 授权过滤器完整覆盖客户端请求的过滤器，防止客户端通过更宽的通配符扩大权限。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.authorization",
        name = "type",
        havingValue = "simple")
public final class SimpleMqttAuthorizer implements MqttAuthorizer {

    private final List<Rule> rules;

    /**
     * 复制并预校验全部授权规则。
     *
     * @param properties 授权配置
     */
    public SimpleMqttAuthorizer(MqttAuthorizationProperties properties) {
        rules = properties.getSimple().getRules().stream()
                .map(SimpleMqttAuthorizer::toRule)
                .toList();
    }

    /**
     * 查找身份匹配且允许目标主题的任一规则。
     *
     * @param request 授权请求
     */
    @Override
    public boolean authorize(MqttAuthorizationRequest request) {
        return rules.stream()
                .filter(rule -> rule.matchesIdentity(request))
                .anyMatch(rule -> rule.allows(request));
    }

    /**
     * 将可变配置规则转换为内部不可变规则。
     *
     * @param source 配置规则
     */
    private static Rule toRule(MqttAuthorizationProperties.Rule source) {
        List<String> publishTopicFilters = copyAndValidate(
                source.getPublishTopicFilters());
        List<String> subscribeTopicFilters = copyAndValidate(
                source.getSubscribeTopicFilters());
        return new Rule(
                source.getClientId(),
                source.getUsername(),
                publishTopicFilters,
                subscribeTopicFilters);
    }

    /**
     * 复制主题过滤器列表，并在启动阶段拒绝非法过滤器。
     *
     * @param topicFilters 主题过滤器列表
     */
    private static List<String> copyAndValidate(List<String> topicFilters) {
        List<String> result = List.copyOf(topicFilters);
        for (String topicFilter : result) {
            if (!MqttTopicFilter.isValid(topicFilter)) {
                throw BaseException.get(
                        "Invalid MQTT authorization topic filter[{}]", topicFilter);
            }
        }
        return result;
    }

    private record Rule(
            String clientId,
            String username,
            List<String> publishTopicFilters,
            List<String> subscribeTopicFilters
    ) {

        /**
         * 判断客户端标识和用户名是否满足规则的身份条件。
         *
         * @param request 授权请求
         */
        private boolean matchesIdentity(MqttAuthorizationRequest request) {
            return (clientId == null || clientId.equals(request.clientId()))
                    && (username == null || username.equals(request.username()));
        }

        /**
         * 根据操作类型使用匹配或覆盖语义判断主题权限。
         *
         * @param request 授权请求
         */
        private boolean allows(MqttAuthorizationRequest request) {
            List<String> allowedFilters = request.action() == MqttAuthorizationAction.PUBLISH
                    ? publishTopicFilters
                    : subscribeTopicFilters;
            return allowedFilters.stream().anyMatch(allowedFilter ->
                    request.action() == MqttAuthorizationAction.PUBLISH
                            ? MqttTopicFilter.matches(allowedFilter, request.topic())
                            : MqttTopicFilter.covers(allowedFilter, request.topic()));
        }
    }
}
