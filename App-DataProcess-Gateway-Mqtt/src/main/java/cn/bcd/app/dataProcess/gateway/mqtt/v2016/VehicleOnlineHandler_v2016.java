package cn.bcd.app.dataProcess.gateway.mqtt.v2016;


import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.parser.protocol.gb32960.v2016.util.PacketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Order(2)
@Component
public class VehicleOnlineHandler_v2016 implements DataHandler_v2016 {

    private static final Logger logger = LoggerFactory.getLogger(VehicleOnlineHandler_v2016.class);

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2016 context) {
        if (flag != PacketFlag.vehicle_run_data) {
            return;
        }
        long collectTimeTs = PacketUtil.getTime(data).getTime();
        if (collectTimeTs < context.lastTimeTs) {
            return;
        }
        context.lastTimeTs = collectTimeTs;
        try {
            redisTemplate.opsForValue().set(Const.redis_key_prefix_vehicle_last_packet_time + vin, context.lastTimeTs + "");
        } catch (Exception e) {
            logger.error("error", e);
        }
    }


    @Override
    public void init(String vin, Context_v2016 context) {
        try {
            String s = redisTemplate.opsForValue().get(Const.redis_key_prefix_vehicle_last_packet_time + vin);
            if (s != null) {
                context.lastTimeTs = Long.parseLong(s);
            }
        } catch (Exception e) {
            logger.error("error", e);
        }
    }
}
