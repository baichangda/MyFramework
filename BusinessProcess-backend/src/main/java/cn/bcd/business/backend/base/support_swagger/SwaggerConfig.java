package cn.bcd.business.backend.base.support_swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${sa-token.token-name}")
    String tokenName;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(
                        new Info().title("API")
                                .description("bcd application")
                                .version("1.0")
                )
                //全局参数设置
//                .components(
//                        new Components().addSecuritySchemes(tokenName, new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(tokenName))
//                )
                ;
    }
}
