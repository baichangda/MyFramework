package cn.bcd.lib.spring.storage.mongo;

import cn.bcd.lib.spring.storage.mongo.raw.RawData;
import cn.bcd.lib.spring.storage.mongo.transfer.TransferData;
import cn.bcd.lib.spring.storage.mongo.transfer.TransferResponseData;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

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
        assertEquals(new Document("_id", new Document("$gte", "raw-0").append("$lt", "raw-9")),
                template.lastQuery.getQueryObject());
        assertEquals(new Document("_id", -1), template.lastQuery.getSortObject());
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
        assertEquals(new Document("_id", "business-key"), template.lastQuery.getQueryObject());
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
        assertEquals(new Document("_id", "missing-key"), template.lastQuery.getQueryObject());
    }

    private static Document stored(String id, String json) {
        return new Document("_id", id).append("value", json);
    }

    @Test
    void saveGroupsByDatabaseAndKeepsLastValueForEachPrimaryKey() {
        RecordingMongoTemplate secondTemplate = new RecordingMongoTemplate();
        MongoUtil.dbNum = 2;
        MongoUtil.mongoTemplates = new MongoTemplate[]{template, secondTemplate};
        RawData first = raw("B", "first");
        RawData otherPartition = raw("D", "other");
        RawData last = raw("B", "last");
        RawData otherDatabase = raw("A", "second-db");

        MongoUtil.save(List.of(first, otherPartition, last, otherDatabase), "rawData");

        assertEquals(1, template.bulkCalls);
        assertEquals(1, secondTemplate.bulkCalls);
        assertEquals(2, template.writes.size());
        assertEquals(1, secondTemplate.writes.size());
        assertEquals("rawData", template.lastCollection);
        assertEquals(1, template.executions);
        assertEquals(1, secondTemplate.executions);
        for (Pair<Query, Update> write : template.writes) {
            String id = write.getFirst().getQueryObject().getString("_id");
            assertNotNull(id);
            assertEquals(1, write.getFirst().getQueryObject().size());
            Document set = (Document) write.getSecond().getUpdateObject().get("$set");
            RawData saved = cn.bcd.lib.base.json.JsonUtil.fromJson(set.getString("value"), RawData.class);
            assertEquals(id.equals(last.getId()) ? "last" : "other", saved.hex);
        }
        assertEquals(new Document("_id", otherDatabase.getId()),
                secondTemplate.writes.getFirst().getFirst().getQueryObject());
    }

    @Test
    void saveEmptyListDoesNotWrite() {
        MongoUtil.save(List.of(), "rawData");
        assertEquals(0, template.bulkCalls);
    }

    private static RawData raw(String vin, String hex) {
        RawData data = new RawData();
        data.vin = vin;
        data.collectTime = new Date(1000);
        data.type = 2;
        data.hex = hex;
        return data;
    }

    private static class RecordingMongoTemplate extends MongoTemplate {
        List<Document> documents = List.of();
        Query lastQuery;
        String lastCollection;
        int bulkCalls;
        int executions;
        List<Pair<Query, Update>> writes = List.of();

        @Override
        @SuppressWarnings("unchecked")
        public BulkOperations bulkOps(BulkOperations.BulkMode mode, String collectionName) {
            assertEquals(BulkOperations.BulkMode.UNORDERED, mode);
            bulkCalls++;
            lastCollection = collectionName;
            return (BulkOperations) Proxy.newProxyInstance(BulkOperations.class.getClassLoader(),
                    new Class<?>[]{BulkOperations.class}, (proxy, method, args) -> {
                        if (method.getName().equals("upsert") && args.length == 1) {
                            writes = (List<Pair<Query, Update>>) args[0];
                            return proxy;
                        }
                        if (method.getName().equals("execute")) {
                            executions++;
                            return null;
                        }
                        throw new AssertionError("Unexpected bulk operation: " + method.getName());
                    });
        }

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
            Object id = query.getQueryObject().get("_id");
            assertNotNull(id, "Lookup must use the MongoDB primary key");
            return documents.stream().filter(d -> id.equals(d.get("_id")))
                    .findFirst().map(entityClass::cast).orElse(null);
        }

        @Override
        public <T> T findById(Object id, Class<T> entityClass, String collectionName) {
            throw new AssertionError("Unexpected findById call");
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
