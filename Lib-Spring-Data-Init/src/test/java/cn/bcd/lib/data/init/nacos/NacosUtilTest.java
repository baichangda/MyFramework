package cn.bcd.lib.data.init.nacos;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.data.init.nacos.ListInstanceRequest;
import cn.bcd.lib.spring.data.init.nacos.NacosUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NacosUtilTest {

    static Logger logger = LoggerFactory.getLogger(NacosUtilTest.class);

    @Test
    public void test() throws Exception {
        ListInstanceRequest listInstanceRequest = new ListInstanceRequest("bus-back");
        logger.info(JsonUtil.toJson(NacosUtil.listInstance("10.0.11.50", 8848, listInstanceRequest)));
    }
}
