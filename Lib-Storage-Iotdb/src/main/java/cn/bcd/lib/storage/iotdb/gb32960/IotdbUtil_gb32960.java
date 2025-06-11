package cn.bcd.lib.storage.iotdb.gb32960;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.storage.iotdb.IotdbConfig;
import com.google.common.base.Function;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.write.record.Tablet;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.apache.tsfile.write.schema.MeasurementSchema;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IotdbUtil_gb32960 {
    public static final String deviceIdPre_rawData = "root.rawData.";
    public static final List<IMeasurementSchema> measurementsSchemas_rawData = List.of(
            new MeasurementSchema("type", TSDataType.INT32),
            new MeasurementSchema("gwReceiveTime", TSDataType.TIMESTAMP),
            new MeasurementSchema("gwSendTime", TSDataType.TIMESTAMP),
            new MeasurementSchema("parseReceiveTime", TSDataType.TIMESTAMP),
            new MeasurementSchema("hex", TSDataType.STRING));

    /**
     * 批量保存
     *
     * @param list
     */
    public static void save_rawData(List<RawData> list) {
        Map<String, Tablet> map = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            RawData rawData = list.get(i);
            Tablet tablet = map.computeIfAbsent(deviceIdPre_rawData + rawData.vin, k -> new Tablet(k, measurementsSchemas_rawData));
            tablet.addTimestamp(i, rawData.collectTime.getTime());
            tablet.addValue(i, 0, rawData.type);
            tablet.addValue(i, 1, rawData.gwReceiveTime.getTime());
            tablet.addValue(i, 2, rawData.gwSendTime.getTime());
            tablet.addValue(i, 3, rawData.parseReceiveTime.getTime());
            tablet.addValue(i, 4, rawData.hex);
        }
        try {
            IotdbConfig.session.insertTablets(map);
        } catch (StatementExecutionException | IoTDBConnectionException ex) {
            throw BaseException.get(ex);
        }
    }

}
