package cn.bcd.lib.spring.data.init.util;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class OkHttpUtil {

    static Logger logger = LoggerFactory.getLogger(OkHttpUtil.class);

    public final static OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(newHttpLoggingInterceptor())
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .build();

    private static HttpLoggingInterceptor newHttpLoggingInterceptor() {
        final HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(s -> {
            logger.info(s);
        });
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        return httpLoggingInterceptor;
    }
}
