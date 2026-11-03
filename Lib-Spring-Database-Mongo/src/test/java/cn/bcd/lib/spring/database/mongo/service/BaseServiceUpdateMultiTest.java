package cn.bcd.lib.spring.database.mongo.service;

import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.BooleanCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NullCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import cn.bcd.lib.spring.database.mongo.anno.DocumentExt;
import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import cn.bcd.lib.spring.database.mongo.bean.SuperBaseBean;
import cn.bcd.lib.spring.database.mongo.bean.UserInterface;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BaseServiceUpdateMultiTest {
    @org.springframework.data.mongodb.core.mapping.Document("test_update_multi")
    static class Bean extends BaseBean {
        public String name;
        public Integer count;
        public Boolean enabled;
    }

    @org.springframework.data.mongodb.core.mapping.Document("test_manual_update_multi")
    @DocumentExt(autoSetUpdateInfo = false)
    static class ManualBean extends BaseBean {
    }

    @org.springframework.data.mongodb.core.mapping.Document("test_plain_update_multi")
    static class PlainBean extends SuperBaseBean {
    }

    static class Service extends BaseService<Bean> {
    }

    static class ManualService extends BaseService<ManualBean> {
    }

    static class PlainService extends BaseService<PlainBean> {
    }

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final Service service = new Service();
    private Supplier<UserInterface> previousSupplier;

    @BeforeEach
    void setUp() {
        previousSupplier = UserGetter.supplier;
        UserGetter.supplier = null;
        service.init(mongo);
    }

    @AfterEach
    void tearDown() {
        UserGetter.supplier = previousSupplier;
    }

    @Test
    void executesOneEntityAwareMultiUpdateAndReturnsOriginalResult() {
        Update update = new Update().set("name", "new").set("other", null).inc("count", 1).unset("email");
        UpdateResult result = UpdateResult.acknowledged(5, 0L, null);
        when(mongo.updateMulti(any(Query.class), same(update), eq(Bean.class))).thenReturn(result);

        assertSame(result, service.updateMulti(StringCondition.EQUAL("name", "old"), update));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateMulti(query.capture(), same(update), eq(Bean.class));
        assertEquals(new Document("name", "old"), query.getValue().getQueryObject());
        Document operators = update.getUpdateObject();
        Document set = (Document) operators.get("$set");
        assertEquals("new", set.get("name"));
        assertTrue(set.containsKey("other"));
        assertNull(set.get("other"));
        assertInstanceOf(Date.class, set.get("updateTime"));
        assertFalse(set.containsKey("updateUserId"));
        assertFalse(set.containsKey("createTime"));
        assertEquals(new Document("count", 1), operators.get("$inc"));
        assertTrue(((Document) operators.get("$unset")).containsKey("email"));
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void nullAndIgnoredConditionsDoNotBecomeCollectionWideUpdates() {
        Condition[] conditions = {
                null,
                NumberCondition.EQUAL("count", null),
                NumberCondition.NOT_IN("count"),
                Condition.and(NumberCondition.EQUAL("count", null), StringCondition.EQUAL("name", null)),
                Condition.or(NumberCondition.NOT_IN("count"), StringCondition.EQUAL("name", null))
        };
        for (Condition condition : conditions) {
            Update update = new Update().set("name", "new");
            String original = update.getUpdateObject().toJson();

            assertZeroResult(service.updateMulti(condition, update));

            assertEquals(original, update.getUpdateObject().toJson());
        }
        verifyNoInteractions(mongo);
    }

    @Test
    void explicitNullAndFalsePredicatesRemainValidFilters() {
        UpdateResult result = UpdateResult.acknowledged(2, 2L, null);
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Bean.class))).thenReturn(result);

        assertSame(result, service.updateMulti(NullCondition.NULL("name"), new Update().set("count", 1)));
        assertSame(result, service.updateMulti(BooleanCondition.EQUAL("enabled", false), new Update().set("count", 1)));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).updateMulti(query.capture(), any(Update.class), eq(Bean.class));
        assertEquals(new Document("$or", List.of(
                new Document("name", new Document("$exists", false)),
                new Document("name", new Document("$type", 10)))), query.getAllValues().getFirst().getQueryObject());
        assertEquals(new Document("enabled", false), query.getAllValues().getLast().getQueryObject());
    }

    @Test
    void emptyUpdateDoesNotAddAuditOrAccessDatabase() {
        Update update = new Update();

        assertZeroResult(service.updateMulti(StringCondition.EQUAL("name", "old"), update));

        assertTrue(update.getUpdateObject().isEmpty());
        verifyNoInteractions(mongo);
    }

    @Test
    void rejectsNullUpdateBeforeDatabaseAccess() {
        assertThrows(NullPointerException.class, () -> service.updateMulti(StringCondition.EQUAL("name", "old"), null));
        verifyNoInteractions(mongo);
    }

    @Test
    void fillsMissingAuditFieldsAndPreservesExplicitAuditOperations() {
        UserGetter.supplier = () -> new UserInterface() {
            public String getId() { return "operator-1"; }
            public String getUsername() { return "operator"; }
        };
        Date explicitTime = new Date(1);
        Update update = new Update().inc("count", 1).set("updateTime", explicitTime).unset("updateUserName");
        when(mongo.updateMulti(any(Query.class), same(update), eq(Bean.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        service.updateMulti(NumberCondition.EQUAL("count", 0), update);

        Document set = (Document) update.getUpdateObject().get("$set");
        assertSame(explicitTime, set.get("updateTime"));
        assertEquals("operator-1", set.get("updateUserId"));
        assertFalse(set.containsKey("updateUserName"));
        assertTrue(((Document) update.getUpdateObject().get("$unset")).containsKey("updateUserName"));
    }

    @Test
    void disabledAuditAndPlainBeansPreserveUpdateAndUnacknowledgedResult() {
        ManualService manual = new ManualService();
        manual.init(mongo);
        PlainService plain = new PlainService();
        plain.init(mongo);
        Update update = new Update().inc("count", 1);
        String original = update.getUpdateObject().toJson();
        UpdateResult result = UpdateResult.unacknowledged();
        when(mongo.updateMulti(any(Query.class), same(update), eq(ManualBean.class))).thenReturn(result);
        when(mongo.updateMulti(any(Query.class), same(update), eq(PlainBean.class))).thenReturn(result);
        Condition condition = NumberCondition.EQUAL("count", 0);

        assertSame(result, manual.updateMulti(condition, update));
        assertSame(result, plain.updateMulti(condition, update));
        assertEquals(original, update.getUpdateObject().toJson());
    }

    private static void assertZeroResult(UpdateResult result) {
        assertTrue(result.wasAcknowledged());
        assertEquals(0, result.getMatchedCount());
        assertEquals(0, result.getModifiedCount());
        assertNull(result.getUpsertedId());
    }
}
