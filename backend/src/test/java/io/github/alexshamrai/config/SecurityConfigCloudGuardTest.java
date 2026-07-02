package io.github.alexshamrai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: the cloud profile must refuse to start with the checked-in admin/admin
 * defaults rather than silently serving the public Cloud Run URL behind them.
 */
class SecurityConfigCloudGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class);

    @Test
    void cloudProfile_withDefaultCredentials_failsToStart() {
        contextRunner
                .withPropertyValues(
                        "music-cat.auth.username=admin",
                        "music-cat.auth.password=admin")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("cloud"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("admin/admin");
                });
    }

    @Test
    void nonCloudProfile_withDefaultCredentials_startsFine() {
        contextRunner
                .withPropertyValues(
                        "music-cat.auth.username=admin",
                        "music-cat.auth.password=admin")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void cloudProfile_withCustomCredentials_startsFine() {
        contextRunner
                .withPropertyValues(
                        "music-cat.auth.username=someone",
                        "music-cat.auth.password=strongpass")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("cloud"))
                .run(context -> assertThat(context).hasNotFailed());
    }
}
