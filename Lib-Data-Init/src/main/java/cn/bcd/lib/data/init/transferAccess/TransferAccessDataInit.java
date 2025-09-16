package cn.bcd.lib.data.init.transferAccess;

import cn.bcd.lib.base.common.Initializable;
import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.InitProp;
import cn.bcd.lib.data.init.nacos.HostData;
import cn.bcd.lib.data.init.nacos.NacosUtil;
import cn.bcd.lib.data.init.util.OkHttpUtil;
import cn.bcd.lib.data.notify.onlyNotify.transferAccess.TransferAccessData;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@EnableConfigurationProperties(InitProp.class)
@ConditionalOnProperty("lib.data.init.transferAccess.enable")
@Component
public class TransferAccessDataInit implements Consumer<TransferAccessData>, Initializable {
    static Logger logger = LoggerFactory.getLogger(TransferAccessDataInit.class);

    public final static ConcurrentHashMap<String, List<String>> vin_platformCodes = new ConcurrentHashMap<>();

    private static InitProp initProp;

    public TransferAccessDataInit(InitProp initProp) {
        TransferAccessDataInit.initProp = initProp;
    }

    @Override
    public int order() {
        return 19;
    }

    @Override
    public void accept(TransferAccessData transferAccessData) {
        if (transferAccessData.getPlatformCode() == null || transferAccessData.getPlatformCode().isEmpty()) {
            vin_platformCodes.remove(transferAccessData.getVin());
        } else {
            vin_platformCodes.put(transferAccessData.getVin(), transferAccessData.getPlatformCode());
        }
    }

    @Override
    public void init() {
        HostData hostData = NacosUtil.getHostData_business_process_backend(initProp.nacosHost, initProp.nacosPort);
        if (hostData == null) {
            return;
        }
        String url = "http://" + hostData.ip + ":" + hostData.port + "/api/transferAccess/list";
        Request request = new Request.Builder()
                .url(url)
                .get().build();
        try (Response response = OkHttpUtil.client.newCall(request).execute()) {
            byte[] bytes = response.body().bytes();
            Result<List<TransferAccessData>> result = JsonUtil.OBJECT_MAPPER.readValue(bytes, new TypeReference<>() {
            });
            if (result.getCode() == 0) {
                List<TransferAccessData> list = result.getData();
                if (list != null && !list.isEmpty()) {
                    for (TransferAccessData data : list) {
                        if (data.getPlatformCode() != null && !data.getPlatformCode().isEmpty()) {
                            vin_platformCodes.put(data.getVin(), data.getPlatformCode());
                        }
                    }
                }
                logger.info("PermissionDataInit succeed、count[{}]", vin_platformCodes.size());
            } else {
                logger.error("TransferAccessDataInit failed、call url[{}] result:\n{}", url, new String(bytes));
            }
        } catch (Exception e) {
            logger.error("TransferAccessDataInit error", e);
        }
    }
}
