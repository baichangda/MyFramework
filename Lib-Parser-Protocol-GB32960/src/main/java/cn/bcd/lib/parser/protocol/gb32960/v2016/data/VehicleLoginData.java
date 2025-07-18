package cn.bcd.lib.parser.protocol.gb32960.v2016.data;


import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.anno.F_date_bytes_6;
import cn.bcd.lib.parser.base.anno.F_num;
import cn.bcd.lib.parser.base.anno.F_string;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.util.Date;
public class VehicleLoginData implements PacketData {
    //数据采集时间
    @F_date_bytes_6
    public Date collectTime;

    //登入流水号
    @F_num(type = NumType.uint16)
    public int sn;

    //iccid
    @F_string(len = 20)
    public String iccid;

    //可充电储能子系统数
    @F_num(type = NumType.uint8, var = 'n')
    public short subSystemNum;

    //可充电储能系统编码长度
    @F_num(type = NumType.uint8, var = 'm')
    public short systemCodeLen;

    //可充电储能系统编码
    @F_string(lenExpr = "n*m")
    public String systemCode;

    public static void main(String[] args) {
        VehicleLoginData vehicleLoginData = new VehicleLoginData();
        vehicleLoginData.collectTime = new Date();
        vehicleLoginData.sn = 1;
        vehicleLoginData.iccid = "00000000000000000000";
        vehicleLoginData.systemCode = "";
        Packet packet = new Packet();
        packet.header = new byte[]{0x23, 0x23};
        packet.flag = PacketFlag.vehicle_login_data;
        packet.replyFlag = 0xfe;
        packet.vin = "LSJE36096MS140495";
        packet.encodeWay = 1;
        packet.contentLength = 30;
        packet.code = 0;
        packet.data = vehicleLoginData;
        Processor<Packet> processor= Parser.getProcessor(Packet.class);
        ByteBuf buffer = Unpooled.buffer();
        processor.deProcess(buffer,packet);
        System.out.println(ByteBufUtil.hexDump(buffer));
    }
}
