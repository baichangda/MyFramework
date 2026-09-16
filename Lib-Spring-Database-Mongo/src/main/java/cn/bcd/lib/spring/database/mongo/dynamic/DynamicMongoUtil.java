package cn.bcd.lib.spring.database.mongo.dynamic;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DynamicMongoUtil {

    /**
     * datasource闲置过期时间
     */
    private static final int EXPIRE_IN_SECOND = 5 * 60;

    private static final Logger logger = LoggerFactory.getLogger(DynamicMongoUtil.class);
    record DynamicMongoData(MongoTemplate mongoTemplate,
                            SimpleMongoClientDatabaseFactory databaseFactory) {
    }

    private static final LoadingCache<String, DynamicMongoData> cache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofSeconds(EXPIRE_IN_SECOND))
            .scheduler(Scheduler.systemScheduler())
            .executor(Runnable::run)
            .<String, DynamicMongoData>removalListener(DynamicMongoUtil::close)
            .build(DynamicMongoUtil::load);

    private static DynamicMongoData load(String url) {
        SimpleMongoClientDatabaseFactory databaseFactory = new SimpleMongoClientDatabaseFactory(url);
        try {
            MappingMongoConverter converter = new MappingMongoConverter(
                    new DefaultDbRefResolver(databaseFactory), new MongoMappingContext());
            converter.setTypeMapper(new DefaultMongoTypeMapper(null));
            converter.afterPropertiesSet();
            MongoTemplate mongoTemplate = new MongoTemplate(databaseFactory, converter);
            logger.info("mongo data source [{}] loaded", mongoTemplate.hashCode());
            return new DynamicMongoData(mongoTemplate, databaseFactory);
        } catch (RuntimeException ex) {
            destroy(databaseFactory);
            throw ex;
        }
    }

    static void close(String ignoredUrl, DynamicMongoData data, RemovalCause cause) {
        if (data != null) {
            destroy(data.databaseFactory());
            logger.info("mongo data source [{}] removed, cause[{}]",
                    data.mongoTemplate().hashCode(), cause);
        }
    }

    private static void destroy(SimpleMongoClientDatabaseFactory databaseFactory) {
        try {
            databaseFactory.destroy();
        } catch (Exception ex) {
            logger.error("close mongo data source failed", ex);
        }
    }



    public static MongoTemplate getMongoTemplate(String url) {
        return cache.get(url).mongoTemplate();
    }

    public static void close(String url) {
        cache.invalidate(url);
    }

    public static void closeAll() {
        cache.invalidateAll();
    }

    public static MongoTemplate getTest() {
        return getMongoTemplate("mongodb://10.0.11.50:27017/ai");
    }

    public static void main(String[] args) throws InterruptedException {
        List<String> dataList1 = getTest().find(new Query().limit(10),String.class,"signal_gb");
        logger.info("{}",dataList1.size());
        List<String> dataList2 = getTest().find(new Query().limit(10),String.class,"signal_gb");
        logger.info("{}",dataList2.size());
        TimeUnit.SECONDS.sleep(10);
        List<String> dataList3 = getTest().find(new Query().limit(10),String.class,"signal_gb");
        logger.info("{}",dataList3.size());
        closeAll();

    }
}
