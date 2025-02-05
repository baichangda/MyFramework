package cn.bcd.businessProcess.backend.base.support_notify;

import cn.bcd.base.json.JsonUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;

public class ListenerInfo {
    public final String id;
    public final String content;
    public long ts;

    @JsonCreator
    public ListenerInfo(@JsonProperty("id") String id, @JsonProperty("content") String content, @JsonProperty("ts") long ts) {
        this.id = id;
        this.content = content;
        this.ts = ts;
    }

    public String toString() {
        return JsonUtil.toJson(this);
    }

    public static ListenerInfo fromString(String str) throws IOException {
        return JsonUtil.OBJECT_MAPPER.readValue(str, ListenerInfo.class);
    }
}
