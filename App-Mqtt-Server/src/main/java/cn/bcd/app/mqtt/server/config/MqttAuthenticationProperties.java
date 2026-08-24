package cn.bcd.app.mqtt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "mqtt.server.authentication")
public class MqttAuthenticationProperties {

    private String type = "anonymous";
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

    public static class Simple {

        private Map<String, String> users = new LinkedHashMap<>();

        public Map<String, String> getUsers() {
            return users;
        }

        public void setUsers(Map<String, String> users) {
            this.users = users;
        }
    }
}
