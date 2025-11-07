package cn.bcd.lib.spring.data.init.nacos;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.data.init.util.OkHttpUtil;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NacosUtil {
    static Logger logger = LoggerFactory.getLogger(NacosUtil.class);

    public static HostData getHostData_business_process_backend(String host, int port) {
        try {
            ListInstanceData listInstanceData = listInstance(host, port, new ListInstanceRequest(Const.service_name_business_process_backend));
            HostData[] hosts = listInstanceData.hosts;
            if (hosts.length == 0) {
                return null;
            } else {
                return hosts[0];
            }
        } catch (Exception ex) {
            logger.error("getHostData_business_process_backend error host[{}] port[{}]", host, port, ex);
            return null;
        }
    }

    public static ListInstanceData listInstance(String host, int port, ListInstanceRequest listInstanceRequest) throws Exception {
        HttpUrl.Builder builder = HttpUrl.get("http://" + host + ":" + port + "/nacos/v2/ns/instance/list")
                .newBuilder();
        if (listInstanceRequest.namespaceId != null) {
            builder.addQueryParameter("namespaceId", listInstanceRequest.namespaceId);
        }
        if (listInstanceRequest.groupName != null) {
            builder.addQueryParameter("groupName", listInstanceRequest.groupName);
        }
        if (listInstanceRequest.serviceName != null) {
            builder.addQueryParameter("serviceName", listInstanceRequest.serviceName);
        }
        if (listInstanceRequest.clusterName != null) {
            builder.addQueryParameter("clusterName", listInstanceRequest.clusterName);
        }
        if (listInstanceRequest.ip != null) {
            builder.addQueryParameter("ip", listInstanceRequest.ip);
        }
        if (listInstanceRequest.port != 0) {
            builder.addQueryParameter("port", listInstanceRequest.port + "");
        }
        if (listInstanceRequest.healthyOnly) {
            builder.addQueryParameter("healthyOnly", true + "");
        }
        if (listInstanceRequest.app != null) {
            builder.addQueryParameter("app", listInstanceRequest.app);
        }
        HttpUrl httpUrl = builder.build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .get()
                .build();
        try (Response response = OkHttpUtil.client.newCall(request).execute()) {
            byte[] bytes = response.body().bytes();
            ListInstanceResponse listInstanceResponse = JsonUtil.OBJECT_MAPPER.readValue(bytes, ListInstanceResponse.class);
            if (listInstanceResponse.code == 0) {
                return listInstanceResponse.getData();
            } else {
                throw BaseException.get("listInstance failed:\n{}", new String(bytes));
            }
        }
    }
}
