package cn.bcd.lib.base.common;

public class Const {

    //微服务网关请求头用户信息
    public final static String request_header_authUser = "authUser";

    //微服务请求前缀
    public final static String uri_prefix = "/service";

    //微服务-后端服务
    public final static String uri_prefix_business_process_backend = uri_prefix + "/backend";
    public final static String service_name_business_process_backend = "business-process-backend";

    //微服务-开放服务
    public final static String uri_prefix_business_process_openapi = uri_prefix + "/openapi";
    public final static String service_name_business_process_openapi = "business-process-openapi";

}
