package cn.bcd.lib.parser.protocol.gb32960.v2025.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PacketFlag {
    vehicle_login_data(0x01, "车辆登入"),
    vehicle_run_data(0x02, "车辆实时信息"),
    vehicle_supplement_data(0x03, "补发信息上报"),
    vehicle_logout_data(0x04, "车辆登出"),
    platform_login_data(0x05, "平台登入"),
    platform_logout_data(0x06, "平台登出"),
    heartbeat(0x07, "心跳"),
    terminal_time_validate(0x08, "终端校时"),
    activate(0x09, "激活"),
    data_encrypt_key_exchange(0x0B, "数据单元加密密钥交换"),
    param_query(0x80, "参数查询"),
    param_set(0x81, "参数设置"),
    terminal_control_command(0x82, "车载终端控制命令"),
    ;

    final static PacketFlag[] all = new PacketFlag[0x82];

    static {
        for (PacketFlag value : PacketFlag.values()) {
            all[value.type] = value;
        }
    }

    @JsonValue
    public final int type;
    public final String remark;

    PacketFlag(int type, String remark) {
        this.type = type;
        this.remark = remark;
    }

    @JsonCreator
    public static PacketFlag fromInteger(int type) {
        return all[type];
    }

    public int toInteger() {
        return type;
    }
}
