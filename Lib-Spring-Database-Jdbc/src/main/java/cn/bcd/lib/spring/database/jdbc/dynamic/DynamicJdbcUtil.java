package cn.bcd.lib.spring.database.jdbc.dynamic;

import cn.bcd.lib.spring.database.jdbc.rowmapper.MyColumnMapRowMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DynamicJdbcUtil {

    /**
     * datasource闲置过期时间
     */
    private final static int EXPIRE_IN_SECOND = 5 * 60;

    /**
     * 数据源最大connection激活数量
     */
    private final static int DATA_SOURCE_MAX_ACTIVE = 3;
    static Logger logger = LoggerFactory.getLogger(DynamicJdbcUtil.class);
    private static final Cache<CacheKey, DynamicJdbcData> CACHE = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofSeconds(EXPIRE_IN_SECOND))
            .<CacheKey, DynamicJdbcData>evictionListener((k, v, c) -> {
                if (k != null && v != null) {
                    //移除数据源时候关闭数据源
                    HikariDataSource dataSource = (HikariDataSource) v.jdbcTemplate().getDataSource();
                    if (dataSource != null) {
                        logger.info("dataSource[{}] [{}] start remove", k.safeKey(), dataSource.hashCode());
                        dataSource.close();
                        logger.info("dataSource[{}] [{}] finish remove", k.safeKey(), dataSource.hashCode());
                    }
                }
            })
            .scheduler(Scheduler.systemScheduler())
            .build();

    private static HikariDataSource getDataSource(String url, String username, String password) {
        //首先测试
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setMinimumIdle(1);
        dataSource.setMaximumPoolSize(DATA_SOURCE_MAX_ACTIVE);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private record CacheKey(String url, String username) {
        String safeKey() {
            return url + "," + username;
        }
    }

    private static CacheKey getKey(String url, String username) {
        Objects.requireNonNull(url);
        Objects.requireNonNull(username);
        return new CacheKey(url, username);
    }

    public static DynamicJdbcData getJdbcData(String url, String username, String password) {
        return CACHE.get(getKey(url, username), key -> {
            //加载新的数据源
            logger.info("dataSource[{}] start load", key.safeKey());
            HikariDataSource dataSource = getDataSource(url, username, password);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.afterPropertiesSet();
            TransactionTemplate transactionTemplate = new TransactionTemplate(new JdbcTransactionManager(dataSource));
            transactionTemplate.afterPropertiesSet();
            DynamicJdbcData jdbcData = new DynamicJdbcData(jdbcTemplate, transactionTemplate);
            logger.info("dataSource[{}] [{}] finish load", key.safeKey(), dataSource.hashCode());
            return jdbcData;
        });
    }

    public static void close(String url, String username) {
        CACHE.invalidate(getKey(url, username));
    }

    public static void closeAll() {
        CACHE.invalidateAll();
    }

    public static DynamicJdbcData getTest() {
        return getJdbcData("jdbc:mysql://127.0.0.1:3306/msbf", "root", "123456");
    }

    public static void main(String[] args) throws InterruptedException {
        List<Map<String, Object>> dataList1 = getTest().jdbcTemplate().query("SELECT * FROM t_sys_user", MyColumnMapRowMapper.ROW_MAPPER);
        List<Map<String, Object>> dataList2 = getTest().jdbcTemplate().query("SELECT * FROM t_sys_user", MyColumnMapRowMapper.ROW_MAPPER);
        TimeUnit.SECONDS.sleep(10);
        List<Map<String, Object>> dataList3 = getTest().jdbcTemplate().query("SELECT * FROM t_sys_user", MyColumnMapRowMapper.ROW_MAPPER);

        closeAll();

    }
}


