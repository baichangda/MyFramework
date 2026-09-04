package cn.bcd.lib.spring.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${spring.application.name:}' != 'app-bp-auth'")
public class AuthAutoConfiguration {
    @Bean
    public AuthenticatedUserFilter authenticatedUserFilter() {
        return new AuthenticatedUserFilter();
    }
}
