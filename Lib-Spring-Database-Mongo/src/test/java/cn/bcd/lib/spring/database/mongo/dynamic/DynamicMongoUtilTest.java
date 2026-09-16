package cn.bcd.lib.spring.database.mongo.dynamic;

import com.github.benmanes.caffeine.cache.RemovalCause;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import static org.mockito.Mockito.*;

class DynamicMongoUtilTest {
    @Test
    void removalClosesOwnedDatabaseFactory() throws Exception {
        MongoTemplate template = mock(MongoTemplate.class);
        SimpleMongoClientDatabaseFactory factory = mock(SimpleMongoClientDatabaseFactory.class);
        var data = new DynamicMongoUtil.DynamicMongoData(template, factory);

        DynamicMongoUtil.close("mongodb://user:secret@localhost/test", data, RemovalCause.EXPLICIT);

        verify(factory).destroy();
    }
}
