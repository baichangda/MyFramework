package cn.bcd.lib.data.init.permission;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.InitProp;
import cn.bcd.lib.data.init.nacos.HostData;
import cn.bcd.lib.data.init.nacos.NacosUtil;
import cn.bcd.lib.data.init.util.OkHttpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@EnableConfigurationProperties(InitProp.class)
@ConditionalOnProperty("lib.data.init.permission.enable")
@Component
public class PermissionDataInit implements ApplicationListener<ContextRefreshedEvent> {

    static Logger logger = LoggerFactory.getLogger(PermissionDataInit.class);


    public static final ConcurrentHashMap<String, PermissionData> resource_permission = new ConcurrentHashMap<>();

    private static InitProp initProp;

    public PermissionDataInit(InitProp initProp) {
        PermissionDataInit.initProp = initProp;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            HostData hostData = NacosUtil.getHostData_business_process_backend(initProp.nacosHost, initProp.nacosPort);
            if (hostData == null) {
                return;
            }
            String url = "http://" + hostData.ip + ":" + hostData.port + "/api/sys/permission/list";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = OkHttpUtil.client.newCall(request).execute()) {
                String str = response.body().string();
                Result<List<PermissionData>> result = JsonUtil.OBJECT_MAPPER.readValue(str, new TypeReference<>() {
                });
                if (result.code == 0) {
                    for (PermissionData data : result.data) {
                        resource_permission.put(data.resource, data);
                    }
                    logger.info("PermissionDataInitializer succeed、count[{}]", resource_permission.size());
                } else {
                    logger.error("PermissionDataInitializer failed、call url[{}] result:\n{}", url, str);
                }
            }
        } catch (Exception ex) {
            logger.error("error", ex);
        }
    }
}
