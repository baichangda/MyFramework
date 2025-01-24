package cn.bcd.storage.mongo.rawData;

import java.util.Date;

public record SaveRawData(String vin, Date collectTime, int type, byte[] value) {

}
