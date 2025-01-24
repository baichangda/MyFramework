package cn.bcd.storage.mongo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

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

}
