package cn.bcd.lib.spring.data.init.transferConfig;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.data.init.InitProp;
import cn.bcd.lib.spring.data.init.nacos.HostData;
import cn.bcd.lib.spring.data.init.nacos.NacosUtil;
import cn.bcd.lib.spring.data.init.util.OkHttpUtil;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@EnableConfigurationProperties(InitProp.class)
@ConditionalOnProperty("lib.spring.data.init.transferConfig.enable")
@Component
public class TransferConfigDataInit {
    static Logger logger = LoggerFactory.getLogger(TransferConfigDataInit.class);

    private static InitProp initProp;

    public TransferConfigDataInit(InitProp initProp) {
        TransferConfigDataInit.initProp = initProp;
    }

    public static TransferConfigData get(String serverId) {
        HostData hostData = NacosUtil.getHostData_business_process_backend(initProp.nacosHost, initProp.nacosPort, Const.service_name_business_process_backend);
        if (hostData == null) {
            return null;
        }
        String url = "http://" + hostData.ip + ":" + hostData.port + "/api/transferConfig/get?serverId=" + serverId;
        Request request = new Request.Builder()
                .url(url)
                .get().build();
        try (Response response = OkHttpUtil.client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw BaseException.get("request failed、response code[{}]", response.code());
            }
            byte[] bytes = response.body().bytes();
            Result<TransferConfigData> result = JsonUtil.OBJECT_MAPPER.readValue(bytes, new TypeReference<>() {
            });
            if (result.getCode() == 0) {
                return result.getData();
            } else {
                logger.error("request failed、call url[{}] result:\n{}", url, new String(bytes));
                return null;
            }
        } catch (Exception e) {
            logger.error("request error", e);
            return null;
        }
    }
}
