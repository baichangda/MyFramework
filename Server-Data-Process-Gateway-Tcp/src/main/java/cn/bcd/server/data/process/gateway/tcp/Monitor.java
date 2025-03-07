package cn.bcd.server.data.process.gateway.tcp;

import java.time.Duration;

public interface Monitor {
    String log(Duration period);

    static String formatSpeed(long count, Duration period) {
        long seconds = period.toSeconds();
        if (count % seconds == 0) {
            return String.valueOf(count / seconds);
        } else {
            return String.valueOf((long)(count * 100 / (double) seconds) / 100d);
        }
    }
}
