package cn.bcd.lib.storage.mongo.gb32960;

import java.util.Date;

public record SaveRawData(String vin, Date collectTime, int type, byte[] value) {

}
