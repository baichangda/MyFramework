package cn.bcd.lib.data.init.nacos;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.data.init.util.OkHttpUtil;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class NacosUtilTest {

    static Logger logger = LoggerFactory.getLogger(NacosUtilTest.class);

    @Test
    public void test() throws Exception {
        ListInstanceRequest listInstanceRequest = new ListInstanceRequest("bus-back");
        logger.info(JsonUtil.toJson(NacosUtil.listInstance("10.0.11.50", 8848, listInstanceRequest)));
    }
}
