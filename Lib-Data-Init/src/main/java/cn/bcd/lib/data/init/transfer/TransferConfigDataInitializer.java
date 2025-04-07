package cn.bcd.lib.data.init.transfer;


import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.InitializerProp;
import cn.bcd.lib.data.init.nacos.HostData;
import cn.bcd.lib.data.init.nacos.NacosUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(InitializerProp.class)
@ConditionalOnProperty("lib.data.init.transferConfig.enable")
@Component
public class TransferConfigDataInitializer {

    static Logger logger = LoggerFactory.getLogger(TransferConfigDataInitializer.class);

    public static OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

    private static InitializerProp initializerProp;

    public TransferConfigDataInitializer(InitializerProp initializerProp) {
        TransferConfigDataInitializer.initializerProp = initializerProp;
    }

    public static TransferConfigData get(String serverId) {
        HostData hostData = NacosUtil.getHostData_business_process_backend(initializerProp.nacosHost, initializerProp.nacosPort);
        if (hostData == null) {
            return null;
        }
        Request request = new Request.Builder()
                .url("http://" + hostData.ip + ":" + hostData.port + "/api/transferConfig/get?serverId=" + serverId)
                .get().build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            byte[] bytes = response.body().bytes();
            Result<TransferConfigData> resultData = JsonUtil.OBJECT_MAPPER.readValue(bytes, new TypeReference<>() {
            });
            if (resultData.getCode() == 0) {
                return resultData.getData();
            } else {
                logger.info("get transferConfig failed,serverId[{}]:\n{}", serverId, new String(bytes));
                return null;
            }
        } catch (Exception e) {
            logger.error("get transferConfig error,serverId[{}]", serverId, e);
            return null;
        }
    }
}
