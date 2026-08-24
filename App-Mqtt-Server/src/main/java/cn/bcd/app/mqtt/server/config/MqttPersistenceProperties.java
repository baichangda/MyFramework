package cn.bcd.app.mqtt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt.server.persistence")
public class MqttPersistenceProperties {

    private RetainedMessage retainedMessage = new RetainedMessage();

    public RetainedMessage getRetainedMessage() {
        return retainedMessage;
    }

    public void setRetainedMessage(RetainedMessage retainedMessage) {
        this.retainedMessage = retainedMessage;
    }

    public static class RetainedMessage {

        private String type = "sqlite";
        private Sqlite sqlite = new Sqlite();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Sqlite getSqlite() {
            return sqlite;
        }

        public void setSqlite(Sqlite sqlite) {
            this.sqlite = sqlite;
        }
    }

    public static class Sqlite {

        private String databasePath = "data/mqtt-retained.db";

        public String getDatabasePath() {
            return databasePath;
        }

        public void setDatabasePath(String databasePath) {
            this.databasePath = databasePath;
        }
    }
}
