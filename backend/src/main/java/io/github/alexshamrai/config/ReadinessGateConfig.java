package io.github.alexshamrai.config;

import io.github.alexshamrai.startup.ReadinessState;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ReadinessGateConfig {

    private static final long READY_TIMEOUT_SECONDS = 25;

    @Bean
    public FilterRegistrationBean<ReadinessGateFilter> readinessGateFilter(ReadinessState readinessState) {
        FilterRegistrationBean<ReadinessGateFilter> registration =
                new FilterRegistrationBean<>(new ReadinessGateFilter(readinessState, READY_TIMEOUT_SECONDS));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
