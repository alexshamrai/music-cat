package io.github.alexshamrai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Every MockMvc request in tests using {@link WithAuthenticatedUser} needs the
 * X-Requested-With header now that {@link io.github.alexshamrai.config.RequireXhrHeaderFilter}
 * rejects state-changing requests without it (see that class for why). The default-request
 * template's own HTTP method is irrelevant — MockMvc merges only its headers/params into
 * whatever request is actually performed.
 */
@TestConfiguration
public class XhrHeaderMockMvcConfig {

    @Bean
    public MockMvcBuilderCustomizer xhrHeaderMockMvcCustomizer() {
        return builder -> builder.defaultRequest(get("/").header("X-Requested-With", "XMLHttpRequest"));
    }
}
