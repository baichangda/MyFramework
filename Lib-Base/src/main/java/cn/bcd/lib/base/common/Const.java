package cn.bcd.lib.base.common;

/**
 * 用于定义模块共享的常量
 */
public class Const {

    public final static boolean logEnable = true;

    //redis key
    public final static String redis_key_prefix_vehicle_last_packet_time = "vlpt:";
    public final static int vehicle_offline_max_time_second = 30;


    //微服务网关请求头用户信息
    public final static String request_header_authUser = "authUser";
    //微服务请求前缀
    public final static String uri_prefix = "/service";
    //微服务-开放服务
    public final static String uri_prefix_business_process_openapi = uri_prefix + "/openapi";
    //微服务-后端服务
    public final static String uri_prefix_business_process_backend = uri_prefix + "/backend";
    public final static String service_name_business_process_backend = "business-process-backend";
    public final static String service_name_business_process_openapi = "business-process-openapi";
}
