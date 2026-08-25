package cn.bcd.app.mqtt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code mqtt.server.persistence} 持久化后端配置。
 */
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

    /** 保留消息存储配置。 */
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

    /** 保留消息 SQLite 数据库配置。 */
    public static class Sqlite {

        private String databasePath = "data/mqtt-retained.db";

        public String getDatabasePath() {
            return databasePath;
        }

        public void setDatabasePath(String databasePath) {
            this.databasePath = databasePath;
        }
    }

    /** 持久会话存储配置。 */
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

    /** 持久会话 SQLite 数据库配置。 */
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
