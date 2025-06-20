package cn.bcd.lib.storage.mongo.transfer;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.storage.mongo.MongoUtil;
import cn.bcd.lib.storage.mongo.QueryData;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public class MongoUtil_transferData {

    static String collection_transferData = "transferData";
    static String collection_transferResponseData = "transferResponseData";


    /**
     * 范围查询
     *
     * @param vin          vin
     * @param beginTime    开始时间 包含 必填
     * @param endTime      结束时间 不包含 必填
     * @param platformCode 平台编码 必填
     * @param skip         跳过前多少条
     * @param limit        限制返回最大条数
     * @param desc         是否主键逆序
     * @return
     */
    public static List<QueryData<TransferData>> list_transferData(String vin, Date beginTime, Date endTime, String platformCode, int skip, int limit, boolean desc) {

        String startId = toId(vin, beginTime, platformCode, 0);
        String endId = toId(vin, endTime, platformCode, 0);
        return MongoUtil.list(vin, startId, endId, skip, limit, desc, TransferData.class, collection_transferData);
    }

    /**
     * 获取单条报文
     *
     * @param vin
     * @param collectTime
     * @param platformCode
     * @param type
     * @return
     */
    public static QueryData<TransferData> get_transferData(String vin, Date collectTime, String platformCode, int type) {
        return MongoUtil.get(vin, toId(vin, collectTime, platformCode, type), TransferData.class, collection_transferData);
    }


    /**
     * 批量保存
     *
     * @param list
     */
    public static void save_transferData(List<TransferData> list) {
        MongoUtil.save(list, collection_transferData);
    }


    /**
     * 范围查询
     *
     * @param vin          vin
     * @param beginTime    开始时间 包含 必填
     * @param endTime      结束时间 不包含 必填
     * @param platformCode 平台编码 必填
     * @param skip         跳过前多少条
     * @param limit        限制返回最大条数
     * @param desc         是否主键逆序
     * @return
     */
    public static List<QueryData<TransferResponseData>> list_transferResponseData(String vin, Date beginTime, Date endTime, String platformCode, int skip, int limit, boolean desc) {

        String startId = toId(vin, beginTime, platformCode, 0);
        String endId = toId(vin, endTime, platformCode, 0);
        return MongoUtil.list(vin, startId, endId, skip, limit, desc, TransferResponseData.class, collection_transferResponseData);
    }

    /**
     * 获取单条报文
     *
     * @param vin
     * @param collectTime
     * @param platformCode
     * @param type
     * @return
     */
    public static QueryData<TransferResponseData> get_transferResponseData(String vin, Date collectTime, String platformCode, int type) {
        return MongoUtil.get(vin, toId(vin, collectTime, platformCode, type), TransferResponseData.class, collection_transferResponseData);
    }


    /**
     * 批量保存
     *
     * @param list
     */
    public static void save_transferResponseData(List<TransferResponseData> list) {
        MongoUtil.save(list, collection_transferResponseData);
    }


    /**
     * 生成记录主键
     *
     * @param vin          vin
     * @param collectTime  采集时间
     * @param platformCode 平台编码
     * @param type         报文类型
     * @return
     */
    public static String toId(String vin, Date collectTime, String platformCode, int type) {
        return Hashing.md5().hashString(vin, StandardCharsets.UTF_8).toString().substring(0, 4)
                + vin
                + Strings.padEnd(platformCode, 20, '#')
                + DateZoneUtil.dateToStr_yyyyMMddHHmmss(collectTime)
                + Strings.padStart(Integer.toHexString(type), 2, '0');
    }
}
