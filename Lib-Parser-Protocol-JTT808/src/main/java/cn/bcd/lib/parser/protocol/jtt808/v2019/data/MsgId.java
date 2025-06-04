package cn.bcd.lib.parser.protocol.jtt808.v2019.data;

public interface MsgId {
    /**
     * 终端通用应答
     */
    int terminal_common_response = 0x0001;

    /**
     * 平台通用应答
     */
    int platform_common_response = 0x8001;

    /**
     * 终端心跳
     */
    int terminal_heartbeat = 0x0002;

    /**
     * 查询服务器时间
     */
    int query_server_time_request = 0x0004;

    /**
     * 查询服务器时间应答
     */
    int query_server_time_response = 0x8004;

    /**
     * 服务器补传分包请求
     */
    int server_sub_packet_request = 0x8003;

    /**
     * 终端补传分包请求
     */
    int terminal_sub_packet_request = 0x0005;

    /**
     * 终端注册
     */
    int terminal_register_request = 0x0100;

    /**
     * 终端注册应答
     */
    int terminal_register_response = 0x8100;

    /**
     * 终端注销
     */
    int terminal_unregister = 0x0003;

    /**
     * 终端鉴权
     */
    int terminal_authentication = 0x0102;

    /**
     * 设置终端参数
     */
    int set_terminal_param = 0x8103;

    /**
     * 查询终端参数
     */
    int query_terminal_param_request = 0x8104;

    /**
     * 查询指定终端参数
     */
    int query_terminal_specify_param_request = 0x8106;

    /**
     * 查询终端参数应答
     */
    int query_terminal_param_response = 0x0104;

    /**
     * 终端控制
     */
    int terminal_control = 0x8105;

    /**
     * 查询终端属性
     */
    int query_terminal_prop_request = 0x8107;

    /**
     * 查询终端属性应答
     */
    int query_terminal_prop_response = 0x0107;

    /**
     * 下发终端升级包
     */
    int issued_terminal_upgrade_request = 0x8108;

    /**
     * 终端升级结果应答
     */
    int terminal_upgrade_res_response = 0x0108;

    /**
     * 位置信息汇报
     */
    int position_data_upload = 0x0200;

    /**
     * 位置信息查询
     */
    int query_position_request = 0x8201;

    /**
     * 位置信息查询应答
     */
    int query_position_response = 0x0201;

    /**
     * 临时位置信息跟踪控制
     */
    int temp_position_follow = 0x8202;

    /**
     * 人工确认报警消息
     */
    int confirm_alarm_msg = 0x8203;

    /**
     * 链路检测
     */
    int link_detection = 0x8204;

    /**
     * 文本信息下发
     */
    int text_info_issued = 0x8300;

    /**
     * 电话回拨
     */
    int phone_callback = 0x8400;

    /**
     * 设置电话本
     */
    int set_phone_text = 0x8401;

    /**
     * 车辆控制
     */
    int vehicle_control_request = 0x8500;

    /**
     * 车辆控制应答
     */
    int vehicle_control_response = 0x0500;

    /**
     * 设置圆形区域
     */
    int set_circle_area = 0x8600;

    /**
     * 删除圆形区域
     */
    int delete_circle_area = 0x8601;

    /**
     * 设置矩形区域
     */
    int set_rectangle_area = 0x8602;

    /**
     * 删除矩形区域
     */
    int delete_rectangle_area = 0x8603;

    /**
     * 设置多边形区域
     */
    int set_polygon_area = 0x8604;

    /**
     * 删除多边形区域
     */
    int delete_polygon_area = 0x8605;

    /**
     * 设置路线
     */
    int set_path = 0x8606;

    /**
     * 删除路线
     */
    int delete_path = 0x8607;

    /**
     * 查询区域或线路数据
     */
    int query_area_or_path_request = 0x8608;

    /**
     * 查询区域或线路数据应答
     */
    int query_area_or_path_response = 0x0608;

    /**
     * 行驶记录数据采集命令
     */
    int driving_recorder_collect_command = 0x8700;

    /**
     * 行驶记录数据上传
     */
    int driving_recorder_upload = 0x0700;

    /**
     * 行驶记录参数下传命令
     */
    int driving_recorder_download_command = 0x8701;

    /**
     * 电子运单上报
     */
    int waybill_report = 0x0701;

    /**
     * 上报驾驶员身份信息请求
     */
    int driver_identity_report_request = 0x8702;

    /**
     * 驾驶员身份信息采集上报
     */
    int driver_identity_report_response = 0x0702;

    /**
     * 定位数据批量上传
     */
    int position_data_batch_upload = 0x0704;

    /**
     * CAN总线数据上传
     */
    int can_data_upload = 0x0705;

    /**
     * 多媒体事件信息上传
     */
    int multimedia_event_upload = 0x0800;

    /**
     * 多媒体数据上传
     */
    int multimedia_data_upload_request = 0x0801;

    /**
     * 多媒体数据上传应答
     */
    int multimedia_data_upload_response = 0x8800;

    /**
     * 摄像头立即拍摄命令
     */
    int camera_take_photo_cmd_request = 0x8801;

    /**
     * 摄像头立即拍摄命令应答
     */
    int camera_take_photo_cmd_response = 0x0805;

    /**
     * 存储多媒体数据检索
     */
    int storage_multimedia_data_fetch_request = 0x8802;

    /**
     * 存储多媒体数据检索应答
     */
    int storage_multimedia_data_fetch_response = 0x0802;

    /**
     * 存储多媒体数据上传命令
     */
    int storage_multimedia_data_upload_cmd = 0x8803;

    /**
     * 录音开始命令
     */
    int recording_start_cmd = 0x8804;

    /**
     * 单条存储多媒体数据检索上传命令
     */
    int single_multimedia_data_fetch_upload_cmd = 0x8805;

    /**
     * 数据下行透传
     */
    int data_down_stream = 0x8900;

    /**
     * 数据上行透传
     */
    int data_up_stream = 0x0900;

    /**
     * 数据压缩上报
     */
    int data_compress_report = 0x0901;

    /**
     * 平台RSA公钥
     */
    int platform_rsa = 0x8a00;

    /**
     * 终端RSA公钥
     */
    int terminal_rsa = 0x0a00;
}
