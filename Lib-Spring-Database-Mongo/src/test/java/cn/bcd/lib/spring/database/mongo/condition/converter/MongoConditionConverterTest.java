package cn.bcd.lib.spring.database.mongo.condition.converter;

import cn.bcd.lib.spring.database.common.condition.impl.NullCondition;
import cn.bcd.lib.spring.database.mongo.util.ConditionUtil;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoConditionConverterTest {
    @Test
    void nullConditionUsesIndependentOrBranches() {
        Document query = ConditionUtil.toQuery(NullCondition.NULL("name")).getQueryObject();

        assertEquals(List.of(
                new Document("name", new Document("$exists", false)),
                new Document("name", new Document("$type", 10))
        ), query.getList("$or", Document.class));
    }

    @Test
    void notNullConditionRequiresExistingNonNullValue() {
        Document query = ConditionUtil.toQuery(NullCondition.NOT_NULL("name")).getQueryObject();

        assertEquals(new Document("name", new Document("$exists", true).append("$ne", null)), query);
    }
}
