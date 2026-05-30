package cn.bcd.lib.spring.storage.influxdb.raw;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.spring.storage.influxdb.InfluxdbConfig;
import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import com.influxdb.v3.client.PointValues;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class InfluxdbUtil_gb32960 {

    private static final DateTimeFormatter RFC3339_NANO = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
            .withZone(ZoneId.of("UTC"));

    private static final String MEASUREMENT = "raw_data";
    private static final String SQL_SELECT = "SELECT time,vin,type,gw_receive_time,gw_send_time,parse_receive_time,hex FROM " + MEASUREMENT;

    private static InfluxDBClient client() {
        return InfluxdbConfig.client;
    }

    private static RawData toRawData(PointValues pointValues) {
        RawData rawData = new RawData();
        rawData.collectTime = new Date(pointValues.getTimestamp().longValue());
        rawData.vin = pointValues.getTag("vin");
        rawData.type = Integer.parseInt(pointValues.getTag("type"));
        rawData.gwReceiveTime = new Date(pointValues.getIntegerField("gw_receive_time"));
        rawData.gwSendTime = new Date(pointValues.getIntegerField("gw_send_time"));
        rawData.parseReceiveTime = new Date(pointValues.getIntegerField("parse_receive_time"));
        rawData.hex = pointValues.getStringField("hex");
        return rawData;
    }

    /**
     * 范围查询
     *
     * @param vin             vin
     * @param beginTime       开始时间 包含 必填
     * @param endTime         结束时间 不包含 必填
     * @param offset          分页偏移量
     * @param pageSize        页大小
     * @param collectTimeDesc 是否时间采集时间降序
     * @return
     */
    public static List<RawData> page_rawData(String vin, Date beginTime, Date endTime, int offset, int pageSize, boolean collectTimeDesc) {
        String sql = String.format(
                "%s WHERE vin = '%s' AND time >= '%s' AND time < '%s' ORDER BY time %s LIMIT %d OFFSET %d",
                SQL_SELECT,
                vin,
                RFC3339_NANO.format(beginTime.toInstant()),
                RFC3339_NANO.format(endTime.toInstant()),
                collectTimeDesc ? "DESC" : "ASC",
                pageSize,
                offset
        );
        List<RawData> list = new ArrayList<>();
        List<PointValues> pointValuesList = client().queryPoints(sql).toList();
        for (PointValues pointValues : pointValuesList) {
            RawData rawData = toRawData(pointValues);
            list.add(rawData);
        }
        return list;
    }

    public static RawData get_rawData(String vin, Date collectTime, int type) {
        String sql = String.format(
                "%s WHERE vin = '%s' AND time = '%s' AND type = %d LIMIT 1",
                SQL_SELECT,
                vin,
                RFC3339_NANO.format(collectTime.toInstant()),
                type
        );
        return client().queryPoints(sql).findFirst().map(e -> toRawData(e)).orElse(null);
    }

    /**
     * 批量保存
     *
     * @param list
     */
    public static void save_rawData(List<RawData> list) {
        if (list == null || list.isEmpty()) {
            return ;
        }
        List<Point> points = new ArrayList<>(list.size());
        for (RawData rawData : list) {
            Point point = Point.measurement(MEASUREMENT)
                    .setTag("vin", rawData.vin)
                    .setTag("type", rawData.type + "")
                    .setField("gw_receive_time", rawData.gwReceiveTime.getTime())
                    .setField("gw_send_time", rawData.gwSendTime.getTime())
                    .setField("parse_receive_time", rawData.parseReceiveTime.getTime())
                    .setField("hex", rawData.hex)
                    .setTimestamp(rawData.collectTime.toInstant());
            points.add(point);
        }
        client().writePoints(points);
    }
}
