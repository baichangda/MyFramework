package cn.bcd.lib.spring.database.mongo.service;

import cn.bcd.lib.spring.database.mongo.anno.DocumentExt;
import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import cn.bcd.lib.spring.database.mongo.bean.UserInterface;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BaseServiceBeanUpdateTest {
    @org.springframework.data.mongodb.core.mapping.Document("bean_update")
    static class Bean extends BaseBean {
        @Field("display_name")
        private String name;
        public int count;
        @ReadOnlyProperty
        public String calculated;
        @Transient
        public String ignored;
        public transient String javaIgnored;
        public static String shared;
        private String getterIgnored;

        @Transient
        public String getGetterIgnored() { return getterIgnored; }
    }

    @org.springframework.data.mongodb.core.mapping.Document("manual_bean_update")
    @DocumentExt(autoSetUpdateInfo = false)
    static class ManualBean extends BaseBean {
    }

    static class Service extends BaseService<Bean> {
    }

    static class ManualService extends BaseService<ManualBean> {
    }

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final Service service = new Service();
    private Supplier<UserInterface> previousSupplier;

    @BeforeEach
    void setUp() {
        previousSupplier = UserGetter.supplier;
        UserGetter.supplier = () -> new UserInterface() {
            public String getId() { return "operator-1"; }
            public String getUsername() { return "operator"; }
        };
        service.init(mongo);
    }

    @AfterEach
    void tearDown() {
        UserGetter.supplier = previousSupplier;
    }

    @Test
    void usesCachedFieldsForIdUpdateIncludingCreateInfo() throws Exception {
        Bean bean = new Bean();
        bean.id = "document-1";
        bean.createTime = new Date(1);
        bean.createUserId = "creator";
        bean.createUserName = "original";
        UpdateResult result = UpdateResult.acknowledged(1, 1L, null);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Bean.class))).thenReturn(result);

        assertSame(result, service.update(bean));

        assertNotNull(bean.updateTime);
        assertEquals("operator-1", bean.updateUserId);
        assertEquals("operator", bean.updateUserName);
        assertEquals(new Date(1), bean.createTime);
        assertEquals("creator", bean.createUserId);
        assertEquals("original", bean.createUserName);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(argThat(query -> "document-1".equals(query.getQueryObject().get("id"))), update.capture(), eq(Bean.class));
        Document changes = (Document) update.getValue().getUpdateObject().get("$set");
        assertTrue(changes.containsKey("name"));
        assertNull(changes.get("name"));
        assertEquals(0, changes.get("count"));
        assertEquals(new Date(1), changes.get("createTime"));
        assertEquals("creator", changes.get("createUserId"));
        assertEquals("original", changes.get("createUserName"));
        assertEquals(bean.updateTime, changes.get("updateTime"));
        assertEquals("operator-1", changes.get("updateUserId"));
        assertEquals("operator", changes.get("updateUserName"));
        for (String excluded : new String[]{"id", "calculated", "ignored", "javaIgnored", "shared", "getterIgnored"}) {
            assertFalse(changes.containsKey(excluded), excluded);
        }
        assertEquals(service.getBeanInfo().updateFieldList.stream().map(java.lang.reflect.Field::getName).toList(),
                changes.keySet().stream().toList());

        var conversions = MongoCustomConversions.create(adapter -> {});
        var mapping = new MongoMappingContext();
        mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mapping.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mapping);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        Document mapped = new UpdateMapper(converter).getMappedObject(update.getValue().getUpdateObject(), mapping.getRequiredPersistentEntity(Bean.class));
        assertTrue(((Document) mapped.get("$set")).containsKey("display_name"));
        assertFalse(((Document) mapped.get("$set")).containsKey("name"));
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void disabledAuditPreservesCallerValuesAndMissingDocumentDoesNotInsert() {
        ManualBean bean = new ManualBean();
        bean.id = "assigned-id";
        bean.updateTime = new Date(1);
        bean.updateUserId = "manual";
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ManualBean.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        ManualService manual = new ManualService();
        manual.init(mongo);

        assertSame(bean, manual.save(bean));

        assertEquals(new Date(1), bean.updateTime);
        assertEquals("manual", bean.updateUserId);
        assertNull(bean.createTime);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(ManualBean.class));
        Document changes = (Document) update.getValue().getUpdateObject().get("$set");
        assertEquals(new Date(1), changes.get("updateTime"));
        assertEquals("manual", changes.get("updateUserId"));
        for (String name : new String[]{"createTime", "createUserId", "createUserName"}) {
            assertTrue(changes.containsKey(name), name);
            assertNull(changes.get(name), name);
        }
        verifyNoMoreInteractions(mongo);
    }

    @Test
    void rejectsMissingBeanOrIdBeforeDatabaseAccess() {
        assertThrows(NullPointerException.class, () -> service.update((Bean) null));
        assertThrows(NullPointerException.class, () -> service.update(new Bean()));
        verifyNoInteractions(mongo);
    }
}
