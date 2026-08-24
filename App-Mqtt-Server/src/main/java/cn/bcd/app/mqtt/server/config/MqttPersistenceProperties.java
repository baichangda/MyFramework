package cn.bcd.app.mqtt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt.server.persistence")
public class MqttPersistenceProperties {

    private RetainedMessage retainedMessage = new RetainedMessage();
    private Session session = new Session();

    public RetainedMessage getRetainedMessage() {
        return retainedMessage;
    }

    public void setRetainedMessage(RetainedMessage retainedMessage) {
        this.retainedMessage = retainedMessage;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
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

    public static class Session {

        private String type = "sqlite";
        private SessionSqlite sqlite = new SessionSqlite();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public SessionSqlite getSqlite() {
            return sqlite;
        }

        public void setSqlite(SessionSqlite sqlite) {
            this.sqlite = sqlite;
        }
    }

    public static class SessionSqlite {

        private String databasePath = "data/mqtt-session.db";

        public String getDatabasePath() {
            return databasePath;
        }

        public void setDatabasePath(String databasePath) {
            this.databasePath = databasePath;
        }
    }
}
