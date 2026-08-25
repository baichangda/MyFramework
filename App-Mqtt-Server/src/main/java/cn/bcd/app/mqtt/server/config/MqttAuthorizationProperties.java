package cn.bcd.app.mqtt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code mqtt.server.authorization} 发布、订阅授权配置。
 */
@ConfigurationProperties(prefix = "mqtt.server.authorization")
public class MqttAuthorizationProperties {

    private String type = "allow-all";
    private Simple simple = new Simple();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Simple getSimple() {
        return simple;
    }

    public void setSimple(Simple simple) {
        this.simple = simple;
    }

    /** 简单授权模式的规则集合。 */
    public static class Simple {

        private List<Rule> rules = new ArrayList<>();

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    /** 单条授权规则，身份字段为空表示不限制对应身份维度。 */
    public static class Rule {

        private String clientId;
        private String username;
        private List<String> publishTopicFilters = new ArrayList<>();
        private List<String> subscribeTopicFilters = new ArrayList<>();

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public List<String> getPublishTopicFilters() {
            return publishTopicFilters;
        }

        public void setPublishTopicFilters(List<String> publishTopicFilters) {
            this.publishTopicFilters = publishTopicFilters;
        }

        public List<String> getSubscribeTopicFilters() {
            return subscribeTopicFilters;
        }

        public void setSubscribeTopicFilters(List<String> subscribeTopicFilters) {
            this.subscribeTopicFilters = subscribeTopicFilters;
        }
    }
}
