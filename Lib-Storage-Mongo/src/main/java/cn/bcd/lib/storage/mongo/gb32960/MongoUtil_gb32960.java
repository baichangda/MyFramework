package cn.bcd.lib.storage.mongo.gb32960;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.storage.mongo.MongoUtil;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class MongoUtil_gb32960 {

    /**
     * 范围查询
     *
     * @param vin       vin
     * @param beginTime 开始时间 包含 必填
     * @param endTime   结束时间 不包含 必填
     * @param skip      跳过前多少条
     * @param limit     限制返回最大条数
     * @param desc      是否主键逆序
     * @return
     */
    public static List<QueryRawData> list_rawData(String vin, Date beginTime, Date endTime, int skip, int limit, boolean desc) {
        String startRowKey = toId_rawData(vin, beginTime, 0);
        String endRowKey = toId_rawData(vin, endTime, 0);
        return list_rawData(vin, startRowKey, endRowKey, skip, limit, desc);
    }

    /**
     * 范围查询
     *
     * @param vin
     * @param startRowKey 包含
     * @param endRowKey   不包含
     * @param skip
     * @param limit
     * @param desc
     * @return
     */
    public static List<QueryRawData> list_rawData(String vin, String startRowKey, String endRowKey, int skip, int limit, boolean desc) {
        MongoTemplate mongoTemplate = MongoUtil.getMongoTemplate(vin);
        Query query = new Query();
        query.skip(skip);
        query.limit(limit);
        query.with(Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, "id"));
        final Criteria criteria = Criteria.where("id");
        criteria.gte(startRowKey);
        criteria.lt(endRowKey);
        query.addCriteria(criteria);
        return mongoTemplate.find(query, QueryRawData.class);
    }

    /**
     * 获取单条报文
     *
     * @param vin
     * @param type
     * @param collectTime
     * @return
     */
    public static QueryRawData get_rawData(String vin, Date collectTime, int type) {
        return MongoUtil.getMongoTemplate(vin).findById(toId_rawData(vin, collectTime, type), QueryRawData.class);
    }


    /**
     * 批量保存
     *
     * @param list
     */
    public static void saveBatch_rawData(List<SaveRawData> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<String, List<Pair<Query, Update>>> map = new HashMap<>();
        for (SaveRawData e : list) {
            List<Pair<Query, Update>> pairs = map.computeIfAbsent(e.vin(), k -> new ArrayList<>());
            String id = toId_rawData(e.vin(), e.collectTime(), e.type());
            pairs.add(Pair.of(Query.query(Criteria.where("id").is(id)), Update.update("value", JsonUtil.toJson(e.val()))));
        }
        map.forEach((k, v) -> {
            MongoUtil.getMongoTemplate(k).bulkOps(BulkOperations.BulkMode.UNORDERED, SaveRawData.class)
                    .upsert(v).execute();
        });
    }

    /**
     * 生成记录主键
     *
     * @param vin         必填
     * @param collectTime 必填
     * @param type        必填
     * @return
     */
    public static String toId_rawData(String vin, Date collectTime, int type) {
        return Hashing.md5().hashString(vin, StandardCharsets.UTF_8).toString().substring(0, 4)
                + Strings.padEnd(vin, 17, '#')
                + DateZoneUtil.dateToString_second(collectTime)
                + Strings.padStart(Integer.toHexString(type), 2, '0');
    }

}
