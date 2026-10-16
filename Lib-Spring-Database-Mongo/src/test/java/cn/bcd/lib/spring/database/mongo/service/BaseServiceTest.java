package cn.bcd.lib.spring.database.mongo.service;

import cn.bcd.lib.spring.database.mongo.bean.BaseBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    static class DerivedService extends Service {
    }

    static class GenericService<T extends cn.bcd.lib.spring.database.mongo.bean.SuperBaseBean> extends BaseService<T> {
    }

    static class ConcreteService extends GenericService<Bean> {
    }

    @Test
    void resolvesEntityThroughInheritance() {
        assertEquals(Bean.class, new DerivedService().getBeanInfo().clazz);
        assertEquals(Bean.class, new ConcreteService().getBeanInfo().clazz);
    }

    @Test
    void largePageDoesNotOverflowAndQueryEarlierRows() {
        when(mongoTemplate.count(any(Query.class), eq(Bean.class))).thenReturn(1L);
        assertTrue(service.page(PageRequest.of(Integer.MAX_VALUE, 2)).isEmpty());
        verify(mongoTemplate, never()).find(any(Query.class), eq(Bean.class));
    }

    @Test
    void largeOffsetIsPassedToMongoAsLong() {
        when(mongoTemplate.count(any(Query.class), eq(Bean.class))).thenReturn(5_000_000_000L);
        when(mongoTemplate.find(any(Query.class), eq(Bean.class))).thenReturn(List.of(new Bean()));
        service.page(PageRequest.of(Integer.MAX_VALUE, 2));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Bean.class));
        assertEquals(4_294_967_294L, query.getValue().getSkip());
    }

    @Test
    void unpagedRequestReturnsContent() {
        when(mongoTemplate.count(any(Query.class), eq(Bean.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Bean.class))).thenReturn(List.of(new Bean()));
        assertEquals(1, service.page(Pageable.unpaged()).getNumberOfElements());
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
