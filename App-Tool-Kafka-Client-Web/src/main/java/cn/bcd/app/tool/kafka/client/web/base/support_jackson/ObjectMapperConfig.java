package cn.bcd.app.tool.kafka.client.web.base.support_jackson;

import cn.bcd.lib.base.json.JsonUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper(){
        return JsonUtil.OBJECT_MAPPER;
    }

}
