package cn.bcd.lib.spring.database.mongo.service;

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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BaseServiceUpdateTest {
    @org.springframework.data.mongodb.core.mapping.Document("test_update")
    static class Bean extends BaseBean {
        public String name;
    }

    @org.springframework.data.mongodb.core.mapping.Document("test_manual_update")
    @DocumentExt(autoSetUpdateInfo = false)
    static class ManualBean extends BaseBean {
    }

    @org.springframework.data.mongodb.core.mapping.Document("test_plain_update")
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
    void returnsOriginalResultAndPreservesSetNullIncAndUnset() {
        Update update = new Update().set("name", null).inc("count", 1).unset("email");
        UpdateResult result = UpdateResult.acknowledged(1, 0L, null);
        when(mongo.updateFirst(any(Query.class), same(update), eq(Bean.class))).thenReturn(result);

        assertSame(result, service.update("document-1", update));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(query.capture(), same(update), eq(Bean.class));
        assertEquals(new Document("id", "document-1"), query.getValue().getQueryObject());
        Document operators = update.getUpdateObject();
        Document set = (Document) operators.get("$set");
        assertTrue(set.containsKey("name"));
        assertNull(set.get("name"));
        assertInstanceOf(Date.class, set.get("updateTime"));
        assertFalse(set.containsKey("createTime"));
        assertFalse(set.containsKey("updateUserId"));
        assertEquals(new Document("count", 1), operators.get("$inc"));
        assertTrue(((Document) operators.get("$unset")).containsKey("email"));
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void fillsAuditFieldsFromCurrentUser() {
        UserGetter.supplier = () -> new UserInterface() {
            public String getId() { return "operator-1"; }
            public String getUsername() { return "operator"; }
        };
        Update update = new Update().set("name", "new");
        when(mongo.updateFirst(any(Query.class), same(update), eq(Bean.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        service.update("document-1", update);

        Document set = (Document) update.getUpdateObject().get("$set");
        assertEquals("operator-1", set.get("updateUserId"));
        assertEquals("operator", set.get("updateUserName"));
        assertInstanceOf(Date.class, set.get("updateTime"));
    }

    @Test
    void preservesExplicitAuditOperations() {
        UserGetter.supplier = () -> mock(UserInterface.class);
        Update update = new Update().set("name", "new").currentDate("updateTime")
                .set("updateUserId", "explicit").unset("updateUserName");
        String original = update.getUpdateObject().toJson();
        when(mongo.updateFirst(any(Query.class), same(update), eq(Bean.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        service.update("document-1", update);

        assertEquals(original, update.getUpdateObject().toJson());
    }

    @Test
    void emptyUpdateDoesNotAddAuditOrAccessDatabase() {
        Update update = new Update();

        UpdateResult result = service.update("document-1", update);

        assertTrue(result.wasAcknowledged());
        assertEquals(0, result.getMatchedCount());
        assertEquals(0, result.getModifiedCount());
        assertNull(result.getUpsertedId());
        assertTrue(update.getUpdateObject().isEmpty());
        verifyNoInteractions(mongo);
    }

    @Test
    void rejectsNullArgumentsBeforeDatabaseAccess() {
        assertThrows(NullPointerException.class, () -> service.update(null, new Update().set("name", "new")));
        assertThrows(NullPointerException.class, () -> service.update("document-1", null));
        verifyNoInteractions(mongo);
    }

    @Test
    void disabledAuditAndPlainBeansKeepOriginalOperatorsAndUnacknowledgedResult() {
        ManualService manual = new ManualService();
        manual.init(mongo);
        PlainService plain = new PlainService();
        plain.init(mongo);
        Update update = new Update().inc("count", 1);
        String original = update.getUpdateObject().toJson();
        UpdateResult result = UpdateResult.unacknowledged();
        when(mongo.updateFirst(any(Query.class), same(update), eq(ManualBean.class))).thenReturn(result);
        when(mongo.updateFirst(any(Query.class), same(update), eq(PlainBean.class))).thenReturn(result);

        assertSame(result, manual.update("document-1", update));
        assertSame(result, plain.update("document-1", update));
        assertEquals(original, update.getUpdateObject().toJson());
    }
}
