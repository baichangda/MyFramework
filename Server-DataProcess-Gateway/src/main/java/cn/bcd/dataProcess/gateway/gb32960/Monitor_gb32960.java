package cn.bcd.dataProcess.gateway.gb32960;

import cn.bcd.base.util.StringUtil;
import cn.bcd.dataProcess.gateway.Monitor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

@Component
public class Monitor_gb32960 implements Monitor {
    public final static LongAdder blockingNum = new LongAdder();
    public final static LongAdder receiveNum = new LongAdder();
    public final static LongAdder sendKafkaNum = new LongAdder();

    @Override
    public String log(Duration period) {
        return StringUtil.format("gb32960 clientNum[{}] blocking[{}] receiveSpeed[{}/s] sendKafkaSpeed[{}/s]",
                Session_gb32960.getSessionMap().size(),
                blockingNum.intValue(),
                Monitor.formatSpeed(receiveNum.sumThenReset(), period),
                Monitor.formatSpeed(sendKafkaNum.sumThenReset(), period));
    }
}
