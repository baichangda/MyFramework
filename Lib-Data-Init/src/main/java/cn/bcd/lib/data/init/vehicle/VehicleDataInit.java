package cn.bcd.lib.data.init.vehicle;

import cn.bcd.lib.base.common.Initializable;
import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.InitProp;
import cn.bcd.lib.data.init.nacos.HostData;
import cn.bcd.lib.data.init.nacos.NacosUtil;
import cn.bcd.lib.data.init.util.OkHttpUtil;
import cn.bcd.lib.data.notify.onlyNotify.vehicleData.VehicleData;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@EnableConfigurationProperties(InitProp.class)
@ConditionalOnProperty("lib.data.init.vehicle.enable")
@Component
public class VehicleDataInit implements Consumer<VehicleData>, Initializable {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(VehicleDataInit.class);

    public static final ConcurrentHashMap<String, VehicleData> vin_vehicleData = new ConcurrentHashMap<>();

    private static InitProp initProp;

    public VehicleDataInit(InitProp staticDataInitProp) {
        VehicleDataInit.initProp = staticDataInitProp;
    }

    @Override
    public int order() {
        return 9;
    }

    public void init() {
        HostData hostData = NacosUtil.getHostData_business_process_backend(initProp.nacosHost, initProp.nacosPort);
        if (hostData == null) {
            return;
        }
        String url = "http://" + hostData.ip + ":" + hostData.port + "/api/vehicle/list";
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = OkHttpUtil.client.newCall(request).execute()) {
            assert response.body() != null;
            byte[] bytes = response.body().bytes();
            Result<List<VehicleData>> resultData = JsonUtil.OBJECT_MAPPER.readValue(bytes, new TypeReference<>() {
            });
            if (resultData.getCode() == 0) {
                List<VehicleData> list = resultData.getData();
                for (VehicleData data : list) {
                    vin_vehicleData.put(data.getVin(), data);
                }
                logger.info("VehicleDataInit succeed、count[{}]", vin_vehicleData.size());
            } else {
                logger.error("VehicleDataInit failed、call url[{}] result:\n{}", url, new String(bytes));
            }
        } catch (IOException e) {
            throw BaseException.get("VehicleDataInit error", e);
        }
    }

    @Override
    public void accept(VehicleData vehicleData) {
        if (vehicleData != null) {
            vin_vehicleData.put(vehicleData.getVin(), vehicleData);
        }
    }
}
