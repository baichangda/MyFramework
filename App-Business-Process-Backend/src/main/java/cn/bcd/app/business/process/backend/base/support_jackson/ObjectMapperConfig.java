package cn.bcd.app.business.process.backend.base.support_jackson;

import cn.bcd.lib.base.json.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper(){
        return JsonUtil.OBJECT_MAPPER;
    }

}
