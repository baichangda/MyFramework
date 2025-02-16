package cn.bcd.server.businessProcess.backend.base.support_notify;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.bcd.lib.base.json.JsonUtil;

import java.io.IOException;
import java.util.function.Consumer;

public class ListenerInfo {
    public final String id;
    public long ts;

    @JsonIgnore
    public Consumer<byte[]> consumer;

    @JsonCreator
    public ListenerInfo(@JsonProperty("id") String id, @JsonProperty("ts") long ts) {
        this(id, ts, null);
    }

    public ListenerInfo(String id, long ts, Consumer<byte[]> consumer) {
        this.id = id;
        this.ts = ts;
        this.consumer = consumer;
    }

    public String toString() {
        return JsonUtil.toJson(this);
    }

    public static ListenerInfo fromString(String str) throws IOException {
        return JsonUtil.OBJECT_MAPPER.readValue(str, ListenerInfo.class);
    }
}
