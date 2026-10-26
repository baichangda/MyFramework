package cn.bcd.lib.spring.storage.mongo;

import cn.bcd.lib.spring.storage.mongo.raw.RawData;
import cn.bcd.lib.spring.storage.mongo.transfer.TransferData;
import cn.bcd.lib.spring.storage.mongo.transfer.TransferResponseData;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;

import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MongoUtilTest {
    private int previousDbNum;
    private MongoTemplate[] previousTemplates;
    private RecordingMongoTemplate template;

    @BeforeEach
    void setUp() {
        previousDbNum = MongoUtil.dbNum;
        previousTemplates = MongoUtil.mongoTemplates;
        template = new RecordingMongoTemplate();
        MongoUtil.dbNum = 1;
        MongoUtil.mongoTemplates = new MongoTemplate[]{template};
    }

    @AfterEach
    void tearDown() {
        MongoUtil.dbNum = previousDbNum;
        MongoUtil.mongoTemplates = previousTemplates;
    }

    @Test
    void listReadsExistingJsonStringsAndKeepsRangeOptions() {
        template.documents = List.of(
                stored("raw-1", "{\"vin\":\"VIN1\",\"type\":2,\"collectTime\":1000,\"hex\":\"2323\"}"),
                stored("raw-2", "{\"vin\":\"VIN1\",\"type\":3,\"collectTime\":2000,\"hex\":\"abcd\"}")
        );

        List<QueryData<RawData>> rows = MongoUtil.list("VIN1", "raw-0", "raw-9",
                2, 10, true, RawData.class, "rawData");

        assertEquals(2, rows.size());
        assertEquals("raw-1", rows.getFirst().id);
        assertEquals("VIN1", rows.getFirst().value.vin);
        assertEquals(new Date(1000), rows.getFirst().value.collectTime);
        assertEquals(2, rows.getFirst().value.type);
        assertEquals("2323", rows.getFirst().value.hex);
        assertEquals("raw-2", rows.get(1).id);
        assertEquals(3, rows.get(1).value.type);
        assertEquals(new Document("id", new Document("$gte", "raw-0").append("$lt", "raw-9")),
                template.lastQuery.getQueryObject());
        assertEquals(new Document("id", -1), template.lastQuery.getSortObject());
        assertEquals(2, template.lastQuery.getSkip());
        assertEquals(10, template.lastQuery.getLimit());
        assertEquals("rawData", template.lastCollection);
    }

    @Test
    void getQueriesBusinessIdAndReadsTransferJson() {
        template.documents = List.of(stored("business-key",
                "{\"vin\":\"VIN2\",\"platformCode\":\"p1\",\"type\":2,\"collectTime\":3000,\"hex\":\"abcd\"}"));

        QueryData<TransferData> row = MongoUtil.get("VIN2", "business-key", TransferData.class, "transferData");

        assertNotNull(row);
        assertEquals("business-key", row.id);
        assertEquals("VIN2", row.value.vin);
        assertEquals("p1", row.value.platformCode);
        assertEquals(new Date(3000), row.value.collectTime);
        assertEquals("abcd", row.value.hex);
        assertEquals(new Document("id", "business-key"), template.lastQuery.getQueryObject());
        assertEquals("transferData", template.lastCollection);
    }

    @Test
    void getReadsTransferResponseJson() {
        template.documents = List.of(stored("response-key",
                "{\"vin\":\"VIN3\",\"platformCode\":\"p2\",\"type\":1,\"replyFlag\":2,\"collectTime\":4000,\"hex\":\"00ff\"}"));

        QueryData<TransferResponseData> row = MongoUtil.get("VIN3", "response-key",
                TransferResponseData.class, "transferResponseData");

        assertNotNull(row);
        assertEquals("response-key", row.id);
        assertEquals("VIN3", row.value.getVin());
        assertEquals("p2", row.value.getPlatformCode());
        assertEquals(2, row.value.getReplyFlag());
        assertEquals(new Date(4000), row.value.getCollectTime());
        assertEquals("00ff", row.value.getHex());
    }

    @Test
    void getReturnsNullWhenBusinessIdIsMissing() {
        template.documents = List.of(stored("another-key", "{\"vin\":\"VIN1\"}"));

        assertNull(MongoUtil.get("VIN1", "missing-key", RawData.class, "rawData"));
        assertEquals(new Document("id", "missing-key"), template.lastQuery.getQueryObject());
    }

    private static Document stored(String id, String json) {
        return new Document("_id", new ObjectId()).append("id", id).append("value", json);
    }

    private static class RecordingMongoTemplate extends MongoTemplate {
        List<Document> documents = List.of();
        Query lastQuery;
        String lastCollection;

        RecordingMongoTemplate() {
            super(databaseFactory(), converter());
        }

        @Override
        public <T> List<T> find(Query query, Class<T> entityClass, String collectionName) {
            lastQuery = query;
            lastCollection = collectionName;
            return documents.stream().map(entityClass::cast).toList();
        }

        @Override
        public <T> T findOne(Query query, Class<T> entityClass, String collectionName) {
            lastQuery = query;
            lastCollection = collectionName;
            Object id = query.getQueryObject().get("id");
            assertNotNull(id, "Lookup must use the stored business id");
            return documents.stream().filter(d -> id.equals(d.get("id")))
                    .findFirst().map(entityClass::cast).orElse(null);
        }

        @Override
        public <T> T findById(Object id, Class<T> entityClass, String collectionName) {
            throw new AssertionError("MongoDB _id differs from the stored business id");
        }

        private static MappingMongoConverter converter() {
            MappingMongoConverter converter = new MappingMongoConverter(
                    NoOpDbRefResolver.INSTANCE, new MongoMappingContext());
            converter.afterPropertiesSet();
            return converter;
        }

        private static MongoDatabaseFactory databaseFactory() {
            return (MongoDatabaseFactory) Proxy.newProxyInstance(
                    MongoDatabaseFactory.class.getClassLoader(),
                    new Class<?>[]{MongoDatabaseFactory.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getExceptionTranslator")) {
                            return new MongoExceptionTranslator();
                        }
                        throw new AssertionError("Unexpected database access: " + method.getName());
                    });
        }
    }
}
