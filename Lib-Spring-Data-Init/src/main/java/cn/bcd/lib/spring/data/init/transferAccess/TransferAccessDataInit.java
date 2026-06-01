package cn.bcd.lib.spring.data.init.transferAccess;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.init.Initializable;
import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.data.init.InitProp;
import cn.bcd.lib.spring.data.init.nacos.HostData;
import cn.bcd.lib.spring.data.init.nacos.NacosUtil;
import cn.bcd.lib.spring.data.init.util.OkHttpUtil;
import cn.bcd.lib.spring.data.notify.onlyNotify.transferAccess.TransferAccessData;
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
@ConditionalOnProperty("lib.spring.data.init.transferAccess.enable")
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
        if(transferAccessData.getVin()==null){
            logger.warn("consume data error、vin is null");
            return;
        }
        if (transferAccessData.getPlatformCode() == null || transferAccessData.getPlatformCode().isEmpty()) {
            vin_platformCodes.remove(transferAccessData.getVin());
        } else {
            vin_platformCodes.put(transferAccessData.getVin(), transferAccessData.getPlatformCode());
        }
    }

    @Override
    public void init() {
        HostData hostData = NacosUtil.getHostData_business_process_backend(initProp.nacosHost, initProp.nacosPort, Const.service_name_business_process_backend);
        if (hostData == null) {
            return;
        }
        String url = "http://" + hostData.ip + ":" + hostData.port + "/api/transferAccess/list";
        Request request = new Request.Builder()
                .url(url)
                .get().build();
        try (Response response = OkHttpUtil.client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw BaseException.get("request failed、response code[{}]", response.code());
            }
            byte[] bytes = response.body().bytes();
            Result<List<TransferAccessData>> result = JsonUtil.OBJECT_MAPPER.readValue(bytes, new TypeReference<>() {
            });
            if (result.getCode() == 0) {
                List<TransferAccessData> list = result.getData();
                if (list == null) {
                    logger.info("request succeed、result data null");
                } else {
                    if (!list.isEmpty()) {
                        for (TransferAccessData data : list) {
                            if (data.getPlatformCode() != null && !data.getPlatformCode().isEmpty()) {
                                vin_platformCodes.put(data.getVin(), data.getPlatformCode());
                            }
                        }
                    }
                    logger.info("request succeed、count[{}]", vin_platformCodes.size());
                }
            } else {
                logger.error("request failed、call url[{}] result:\n{}", url, new String(bytes));
            }
        } catch (Exception e) {
            logger.error("request error", e);
        }
    }
}
