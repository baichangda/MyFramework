package cn.bcd.lib.spring.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(AuthAutoConfiguration.class);

    @Test
    void disablesContextForAuthApplication() {
        contextRunner.withPropertyValues("spring.application.name=app-bp-auth")
                .run(context -> assertThat(context).doesNotHaveBean(AuthAutoConfiguration.class));
    }

    @Test
    void enablesContextForBusinessApplication() {
        contextRunner.withPropertyValues("spring.application.name=businessProcess-backend")
                .run(context -> assertThat(context).hasSingleBean(AuthAutoConfiguration.class));
    }

    @Test
    void enablesContextForOtherMicroservice() {
        contextRunner.withPropertyValues("spring.application.name=another-service")
                .run(context -> assertThat(context).hasSingleBean(AuthAutoConfiguration.class));
    }
}
