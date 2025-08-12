package cn.bcd.app.dataProcess.gateway.tcp.v2025;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2025.util.PacketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class VehicleOnlineHandler_v2025 implements DataHandler_v2025 {

    static final Logger logger = LoggerFactory.getLogger(VehicleOnlineHandler_v2025.class);

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2025 context) throws Exception {
        if (flag != PacketFlag.vehicle_run_data) {
            return;
        }
        long timeTs = PacketUtil.getTime(data).getTime();
        if (timeTs < context.lastTimeTs) {
            return;
        }
        context.lastTimeTs = timeTs;
        try {
            redisTemplate.opsForValue().set(Const.redis_key_prefix_vehicle_last_packet_time + vin, context.lastTimeTs + "");
        } catch (Exception e) {
            logger.error("error", e);
        }
    }
}
