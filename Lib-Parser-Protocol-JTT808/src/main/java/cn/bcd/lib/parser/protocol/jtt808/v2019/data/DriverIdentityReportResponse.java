package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.data.NumType;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_date_bytes_6;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_string;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_string_bcd;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class DriverIdentityReportResponse implements PacketBody {
    //状态
    @F_num(type = NumType.uint8)
    public byte status;
    //时间
    public Date time;
    //ic卡读取结果
    public byte res;
    //驾驶员姓名长度
    public short nameLen;
    //驾驶员姓名
    public String name;
    //从业资格证编码
    public String code;
    //发证机构名称长度
    public short orgLen;
    //发证机构
    public String org;
    //证件有效期
    public String expired;
    //驾驶员身份证号
    public String id;

    public static DriverIdentityReportResponse read(ByteBuf data) {
        DriverIdentityReportResponse driverIdentityReportResponse = new DriverIdentityReportResponse();
        driverIdentityReportResponse.status = data.readByte();
        driverIdentityReportResponse.time = new Date(FieldBuilder__F_date_bytes_6.read(data, DateZoneUtil.ZONE_OFFSET, 2000));
        driverIdentityReportResponse.res = data.readByte();
        if (driverIdentityReportResponse.res == 0) {
            driverIdentityReportResponse.nameLen = data.readUnsignedByte();
            driverIdentityReportResponse.name = data.readCharSequence(driverIdentityReportResponse.nameLen, StandardCharsets.UTF_8).toString();
            driverIdentityReportResponse.code = FieldBuilder__F_string.read_highAddressAppend(data, 20, StandardCharsets.UTF_8);
            driverIdentityReportResponse.orgLen = data.readUnsignedByte();
            driverIdentityReportResponse.org = data.readCharSequence(driverIdentityReportResponse.orgLen, StandardCharsets.UTF_8).toString();
            driverIdentityReportResponse.expired = FieldBuilder__F_string_bcd.read_noAppend(data, 4);
            driverIdentityReportResponse.id = FieldBuilder__F_string.read_highAddressAppend(data, 20, StandardCharsets.UTF_8);
        }
        return driverIdentityReportResponse;
    }

    public void write(ByteBuf data) {
        data.writeByte(status);
        FieldBuilder__F_date_bytes_6.write(data, time.getTime(), DateZoneUtil.ZONE_OFFSET, 2000);
        data.writeByte(res);
        if (res == 0) {
            data.writeByte(nameLen);
            data.writeCharSequence(name, StandardCharsets.UTF_8);
            FieldBuilder__F_string.write_highAddressAppend(data, code, 20, StandardCharsets.UTF_8);
            data.writeByte(orgLen);
            data.writeCharSequence(org, StandardCharsets.UTF_8);
            FieldBuilder__F_string_bcd.write_noAppend(data, expired);
            FieldBuilder__F_string.write_highAddressAppend(data, id, 20, StandardCharsets.UTF_8);
        }
    }
}
