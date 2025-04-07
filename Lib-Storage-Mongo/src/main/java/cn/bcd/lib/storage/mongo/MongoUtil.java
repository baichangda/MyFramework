package cn.bcd.lib.storage.mongo;

import cn.bcd.lib.base.json.JsonUtil;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(value = "mongodbs")
public class MongoUtil {

    public static int dbNum;
    public static MongoTemplate[] mongoTemplates;

    public MongoUtil(@Value("${mongodbs}") String[] mongodbs) {
        dbNum = mongodbs.length;
        mongoTemplates = new MongoTemplate[dbNum];
        for (int i = 0; i < dbNum; i++) {
            SimpleMongoClientDatabaseFactory simpleMongoClientDatabaseFactory = new SimpleMongoClientDatabaseFactory(mongodbs[i]);
            MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(simpleMongoClientDatabaseFactory), new MongoMappingContext());
            converter.setTypeMapper(new DefaultMongoTypeMapper(null));
            mongoTemplates[i] = new MongoTemplate(simpleMongoClientDatabaseFactory, converter);
        }
    }

    public static MongoTemplate getMongoTemplate(String vin) {
        return mongoTemplates[MongoUtil.getDbIndex(vin, dbNum)];
    }

    public static int getDbIndex(String vin, int dbNum) {
        return Math.floorMod(vin.hashCode(), dbNum);
    }


    /**
     * 范围查询
     *
     * @param partitionId partitionId
     * @param startId     开始id
     * @param endId       结束id
     * @param skip        跳过前多少条
     * @param limit       限制返回最大条数
     * @param desc        是否主键逆序
     * @param clazz
     * @return
     */
    public static <T extends MongoData> List<QueryData<T>> list(String partitionId, String startId, String endId, int skip, int limit, boolean desc, Class<T> clazz, String collection) {
        MongoTemplate mongoTemplate = getMongoTemplate(partitionId);
        Query query = new Query();
        query.skip(skip);
        query.limit(limit);
        query.with(Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, "id"));
        final Criteria criteria = Criteria.where("id");
        criteria.gte(startId);
        criteria.lt(endId);
        query.addCriteria(criteria);
        List<Document> documents = mongoTemplate.find(query, Document.class, collection);
        return documents.stream().map(document -> {
            QueryData<T> queryData = new QueryData<>();
            queryData.id = document.getString("id");
            queryData.value = document.get("value", clazz);
            return queryData;
        }).toList();
    }

    /**
     * 获取单条数据
     *
     * @param partitionId
     * @param id
     * @param clazz
     * @return
     */
    public static <T extends MongoData> QueryData<T> get(String partitionId, String id, Class<T> clazz, String collection) {
        Document document = getMongoTemplate(partitionId).findById(id, Document.class, collection);
        if (document == null) {
            return null;
        } else {
            QueryData<T> queryData = new QueryData<>();
            queryData.id = document.getString("id");
            queryData.value = document.get("value", clazz);
            return queryData;
        }
    }

    public static void save(List<? extends MongoData> list, String collection) {
        if (list.isEmpty()) {
            return;
        }
        Map<String, List<Pair<Query, Update>>> map = new HashMap<>();

        for (MongoData mongoData : list) {
            List<Pair<Query, Update>> pairs = map.computeIfAbsent(mongoData.getPartitionId(), k -> new ArrayList<>());
            String id = mongoData.getId();
            pairs.add(Pair.of(Query.query(Criteria.where("id").is(id)), Update.update("value", JsonUtil.toJson(mongoData))));
        }
        map.forEach((k, v) -> {
            MongoUtil.getMongoTemplate(k).bulkOps(BulkOperations.BulkMode.UNORDERED, collection)
                    .upsert(v).execute();
        });
    }
}
