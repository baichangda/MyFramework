package cn.bcd.server.data.process.gateway.tcp;

import cn.bcd.lib.base.util.StringUtil;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

public class Monitor {

    public final static LongAdder blockingNum = new LongAdder();
    public final static LongAdder receiveNum = new LongAdder();
    public final static LongAdder sendKafkaNum = new LongAdder();

    public String log(Duration period) {
        return StringUtil.format("gb32960 clientNum[{}] blocking[{}] receiveSpeed[{}/s] sendKafkaSpeed[{}/s]",
                Session.sessionMap.size(),
                blockingNum.intValue(),
                Monitor.formatSpeed(receiveNum.sumThenReset(), period),
                Monitor.formatSpeed(sendKafkaNum.sumThenReset(), period));
    }

    static String formatSpeed(long count, Duration period) {
        long seconds = period.toSeconds();
        if (count % seconds == 0) {
            return String.valueOf(count / seconds);
        } else {
            return String.valueOf((long)(count * 100 / (double) seconds) / 100d);
        }
    }
}
