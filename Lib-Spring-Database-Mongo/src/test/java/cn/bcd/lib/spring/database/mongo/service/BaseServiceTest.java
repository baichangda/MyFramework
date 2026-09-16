package cn.bcd.lib.spring.database.mongo.service;

import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BaseServiceTest {
    @Document("test_bean")
    static class Bean extends BaseBean {
        public String name;
    }

    static class Service extends BaseService<Bean> {
    }

    private MongoTemplate mongoTemplate;
    private Service service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        service = new Service();
        service.init(mongoTemplate);
    }

    @Test
    void singleIdDeleteUsesEntityAwareQueryOverload() {
        service.delete("id-1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(query.capture(), eq(Bean.class));
        assertEquals("id-1", query.getValue().getQueryObject().get("id"));
        verify(mongoTemplate, never()).remove(any(Object.class));
    }

    @Test
    void assignedIdForMissingDocumentGetsCreateAuditFields() {
        Bean bean = new Bean();
        bean.id = "assigned-id";
        when(mongoTemplate.exists(any(Query.class), eq(Bean.class))).thenReturn(false);
        when(mongoTemplate.save(bean)).thenReturn(bean);

        assertSame(bean, service.save(bean));

        assertNotNull(bean.createTime);
        assertNull(bean.updateTime);
        verify(mongoTemplate).exists(any(Query.class), eq(Bean.class));
        verify(mongoTemplate).save(bean);
    }

    @Test
    void assignedIdForExistingDocumentGetsUpdateAuditFields() {
        Bean bean = new Bean();
        bean.id = "existing-id";
        bean.createTime = new Date(1);
        when(mongoTemplate.exists(any(Query.class), eq(Bean.class))).thenReturn(true);
        when(mongoTemplate.save(bean)).thenReturn(bean);

        service.save(bean);

        assertEquals(new Date(1), bean.createTime);
        assertNotNull(bean.updateTime);
    }

    @Test
    void generatedIdInsertDoesNotQueryForExistence() {
        Bean bean = new Bean();
        when(mongoTemplate.save(bean)).thenReturn(bean);

        service.save(bean);

        assertNotNull(bean.createTime);
        verify(mongoTemplate, never()).exists(any(Query.class), any(Class.class));
    }
}
