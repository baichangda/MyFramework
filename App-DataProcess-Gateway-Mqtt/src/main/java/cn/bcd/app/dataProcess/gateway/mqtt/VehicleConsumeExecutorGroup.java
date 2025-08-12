package cn.bcd.app.dataProcess.gateway.mqtt;

import cn.bcd.app.dataProcess.gateway.mqtt.v2016.DataHandler_v2016;
import cn.bcd.app.dataProcess.gateway.mqtt.v2016.VehicleEntity_v2016;
import cn.bcd.app.dataProcess.gateway.mqtt.v2025.DataHandler_v2025;
import cn.bcd.app.dataProcess.gateway.mqtt.v2025.VehicleEntity_v2025;
import cn.bcd.lib.base.executor.BlockingChecker;
import cn.bcd.lib.base.executor.consume.ConsumeEntity;
import cn.bcd.lib.base.executor.consume.ConsumeExecutorGroup;
import cn.bcd.lib.data.init.vehicle.VehicleDataInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


@Component
public class VehicleConsumeExecutorGroup extends ConsumeExecutorGroup<byte[]> {

    private static final Logger logger = LoggerFactory.getLogger(VehicleConsumeExecutorGroup.class.getName());

    final List<DataHandler_v2016> handlers_v2016;
    final List<DataHandler_v2025> handlers_v2025;

    public VehicleConsumeExecutorGroup(List<DataHandler_v2016> handlers_v2016, List<DataHandler_v2025> handlers_v2025) {
        super("vehicleConsume",
                Runtime.getRuntime().availableProcessors(),
                0,
                false,
                BlockingChecker.DEFAULT,
                EntityScanner.get(300, 60),
                5);
        this.handlers_v2016=handlers_v2016;
        this.handlers_v2025=handlers_v2025;
        logger.info("""
                ---------DataHandler_v2016---------
                {}
                -----------------------------------
                """, handlers_v2016.stream().map(e -> e.getClass().getName()).collect(Collectors.joining("\n")));

        logger.info("""
                ---------DataHandler_v2025---------
                {}
                -----------------------------------
                """, handlers_v2025.stream().map(e -> e.getClass().getName()).collect(Collectors.joining("\n")));
        init();
    }

    @Override
    public String id(byte[] bytes) {
        return new String(bytes, 4, 17);
    }

    @Override
    public void onMessage(byte[] bytes) {
        String vin = new String(bytes, 4, 17);
        if (!VehicleDataInit.vin_vehicleData.containsKey(vin)) {
            logger.error("vin[{}] not found、discard data", vin);
            return;
        }
        super.onMessage(bytes);
    }

    @Override
    public ConsumeEntity<byte[]> newEntity(String id, byte[] first) {
        if (first[0] == 0x23 && first[1] == 0x23) {
            return new VehicleEntity_v2016(id, handlers_v2016);
        }else{
            return new VehicleEntity_v2025(id, handlers_v2025);
        }
    }
}
