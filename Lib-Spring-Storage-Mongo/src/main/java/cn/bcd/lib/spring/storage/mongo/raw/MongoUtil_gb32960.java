package cn.bcd.lib.spring.storage.mongo.raw;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.spring.storage.mongo.MongoUtil;
import cn.bcd.lib.spring.storage.mongo.QueryData;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public class MongoUtil_gb32960 {

    static String collection_rowData = "rawData";


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
    public static List<QueryData<RawData>> list_rawData(String vin, Date beginTime, Date endTime, int skip, int limit, boolean desc) {
        String startId = toId(vin, beginTime, 0);
        String endId = toId(vin, endTime, 0);
        return MongoUtil.list(vin, startId, endId, skip, limit, desc, RawData.class, collection_rowData);
    }

    /**
     * 获取单条报文
     *
     * @param vin         vin
     * @param collectTime 采集时间
     * @param type        报文类型
     * @return
     */
    public static QueryData<RawData> get_rawData(String vin, Date collectTime, int type) {
        return MongoUtil.get(vin, toId(vin, collectTime, type), RawData.class, collection_rowData);
    }


    /**
     * 批量保存
     *
     * @param list
     */
    public static void save_rawData(List<RawData> list) {
        MongoUtil.save(list, collection_rowData);
    }

    /**
     * 生成记录主键
     *
     * @param vin         vin
     * @param collectTime 采集时间
     * @param type        报文类型
     * @return
     */
    public static String toId(String vin, Date collectTime, int type) {
        return Hashing.md5().hashString(vin, StandardCharsets.UTF_8).toString().substring(0, 4)
                + vin
                + DateZoneUtil.dateToStr_yyyyMMddHHmmss(collectTime)
                + Strings.padStart(Integer.toHexString(type), 2, '0');
    }
}
