package cn.bcd.app.bp.backend.base.support_jackson;

import cn.bcd.lib.base.json.JsonUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapper objectMapper(){
        return JsonUtil.MAPPER;
    }

}
