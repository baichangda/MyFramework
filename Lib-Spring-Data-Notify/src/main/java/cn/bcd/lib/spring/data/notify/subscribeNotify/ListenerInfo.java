package cn.bcd.lib.spring.data.notify.subscribeNotify;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.bcd.lib.base.json.JsonUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public class ListenerInfo {
    public final String id;
    public final String clientId;
    public long ts;

    @JsonIgnore
    public Consumer<byte[]> consumer;

    @JsonCreator
    public ListenerInfo(@JsonProperty("id") String id,
                        @JsonProperty("clientId") String clientId,
                        @JsonProperty("ts") long ts) {
        this(id, clientId, ts, null);
    }

    public ListenerInfo(String id, String clientId, long ts, Consumer<byte[]> consumer) {
        this.id = Objects.requireNonNull(id, "id");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.ts = ts;
        this.consumer = consumer;
    }

    public String redisField() {
        return redisFieldPrefix(clientId) + id;
    }

    public static String redisFieldPrefix(String clientId) {
        Objects.requireNonNull(clientId, "clientId");
        return clientId + ",";
    }

    public String toString() {
        return JsonUtil.toJson(this);
    }

    public static ListenerInfo fromString(String str) throws IOException {
        return JsonUtil.MAPPER.readValue(str, ListenerInfo.class);
    }
}
