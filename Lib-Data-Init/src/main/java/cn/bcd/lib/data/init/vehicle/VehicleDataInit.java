package cn.bcd.lib.data.init.vehicle;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.InitProp;
import cn.bcd.lib.data.init.nacos.HostData;
import cn.bcd.lib.data.init.nacos.NacosUtil;
import cn.bcd.lib.data.notify.onlyNotify.vehicleData.VehicleData;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@EnableConfigurationProperties(InitProp.class)
@ConditionalOnProperty("lib.data.init.vehicle.enable")
@Component
public class VehicleDataInit implements Consumer<VehicleData>, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(VehicleDataInit.class);

    public static final ConcurrentHashMap<String, VehicleData> VEHICLE_DATA_MAP = new ConcurrentHashMap<>();

    private static InitProp initProp;

    public VehicleDataInit(InitProp staticDataInitProp) {
        VehicleDataInit.initProp = staticDataInitProp;
    }

    public void onApplicationEvent(ContextRefreshedEvent event) {
        HostData hostData = NacosUtil.getHostData_business_process_backend(initProp.nacosHost, initProp.nacosPort);
        if (hostData == null) {
            return;
        }
        OkHttpClient httpClient = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url("http://" + hostData.ip + ":" + hostData.port + "/api/vehicle/vehicleData/initVehicleData").get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            assert response.body() != null;
            String res = response.body().string();
            Result<List<VehicleData>> resultData = JsonUtil.OBJECT_MAPPER.readValue(res, new TypeReference<>() {
            });
            if (resultData.getCode() == 0) {
                List<VehicleData> list = resultData.getData();
                for (VehicleData data : list) {
                    VEHICLE_DATA_MAP.put(data.getVin(), data);
                }
                logger.info("VehicleData init succeed, total size :{}", list.size());
            } else {
                logger.error("VehicleData init failed:\n{}", res);
            }
        } catch (IOException e) {
            throw BaseException.get("车辆数据加载异常，请检查！！！", e);
        }
    }

    @Override
    public void accept(VehicleData vehicleData) {
        if (vehicleData != null) {
            VEHICLE_DATA_MAP.put(vehicleData.getVin(), vehicleData);
        }
    }
}
