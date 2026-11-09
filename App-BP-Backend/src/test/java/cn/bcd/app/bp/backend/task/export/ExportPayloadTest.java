package cn.bcd.app.bp.backend.task.export;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;

class ExportPayloadTest {
    @Test
    void preservesAllOrderOptionsAndOrderSequence() {
        Sort sort = Sort.by(
                Sort.Order.desc("name").ignoreCase().nullsLast(),
                Sort.Order.asc("createdTime").nullsFirst(),
                Sort.Order.asc("id"));
        ExportPayload payload = new ExportPayload(null, sort);
        String json = JsonUtil.toJson(payload);
        assertTrue(json.contains("\"orders\":["));
        assertTrue(json.contains("\"property\":\"name\""));
        assertEquals(sort, JsonUtil.fromJson(json, ExportPayload.class).sort.toSort());
        assertEquals(sort, JsonUtil.buildObjectMapper().readValue(json, ExportPayload.class).sort.toSort());
    }

    @Test
    void defaultsNullMissingAndEmptySort() {
        ExportPayload unsorted = new ExportPayload(null, null);
        assertEquals(Sort.unsorted(), unsorted.sort.toSort());
        String[] jsonValues = {JsonUtil.toJson(unsorted), "{}", "{\"sort\":null}", "{\"sort\":{\"orders\":[]}}"};
        for (String json : jsonValues) {
            ExportPayload restored = JsonUtil.fromJson(json, ExportPayload.class);
            assertNull(restored.condition);
            assertEquals(Sort.unsorted(), restored.sort.toSort());
        }
    }

    @Test
    void restoresConditionAndSortWithoutCallerConversion() {
        ExportPayload original = new ExportPayload(
                Condition.or(StringCondition.EQUAL("name", "Alice"), StringCondition.IN("name", "Bob")),
                Sort.by(Sort.Order.desc("createdTime"), Sort.Order.asc("id")));
        ExportPayload restored = JsonUtil.fromJson(JsonUtil.toJson(original), ExportPayload.class);
        assertEquals(original.sort, restored.sort);
        assertEquals(JsonUtil.toJson(original.condition), JsonUtil.toJson(restored.condition));
    }

    @Test
    void defaultsMissingOrderOptions() {
        ExportPayload restored = JsonUtil.fromJson(
                "{\"sort\":{\"orders\":[{\"property\":\"id\"}]}}", ExportPayload.class);
        assertEquals(Sort.by("id"), restored.sort.toSort());
    }

    @Test
    void rejectsMalformedOrdersAndLegacyLossyFormat() {
        String[] invalid = {
                "[null]",
                "[{}]",
                "[{\"property\":\" \"}]",
                "[{\"property\":\"id\",\"direction\":\"UNKNOWN\"}]",
                "[{\"property\":\"id\",\"nullHandling\":\"UNKNOWN\"}]"
        };
        for (String sort : invalid) {
            String json = "{\"sort\":{\"orders\":" + sort + "}}";
            assertThrows(RuntimeException.class, () -> JsonUtil.fromJson(json, ExportPayload.class), json);
        }
        assertThrows(RuntimeException.class, () -> JsonUtil.fromJson(
                "{\"sort\":{\"empty\":false,\"sorted\":true,\"unsorted\":false}}", ExportPayload.class));
    }
}
