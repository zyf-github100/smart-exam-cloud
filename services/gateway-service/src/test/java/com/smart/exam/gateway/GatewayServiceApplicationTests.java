package com.smart.exam.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = GatewayServiceApplication.class,
        properties = {
                "spring.config.import=",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef"
        }
)
class GatewayServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldLoadApplicationContext() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBean(GatewayServiceApplication.class)).isNotNull();
    }
}
