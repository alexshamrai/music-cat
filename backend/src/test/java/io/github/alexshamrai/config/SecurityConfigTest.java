package io.github.alexshamrai.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the single-user HTTP Basic protection: every path requires credentials —
 * API, UI, everything (the Cloud Run URL is public).
 *
 * <p>Credentials default to admin/admin locally (application.yml); in production they
 * come from MUSIC_CAT_USER / MUSIC_CAT_PASSWORD env vars.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiWithoutCredentials_returns401WithBasicChallenge() throws Exception {
        mockMvc.perform(get("/api/browse/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.startsWith("Basic")));
    }

    @Test
    void apiWithCorrectCredentials_returns200() throws Exception {
        mockMvc.perform(get("/api/browse/stats").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void apiWithWrongPassword_returns401() throws Exception {
        mockMvc.perform(get("/api/browse/stats").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rootIndexWithoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== blind cross-site form CSRF guard ====================

    @Test
    void postWithoutXhrHeader_returns403_evenWithValidCredentials() throws Exception {
        // Simulates a blind cross-site <form method=POST> — no custom header possible —
        // riding on cached Basic credentials. Must be rejected before reaching the controller.
        mockMvc.perform(post("/api/catalog/sync/push").with(httpBasic("admin", "admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithXhrHeader_reachesController() throws Exception {
        // Sheets is disabled in the test profile, so reaching the controller means 503
        // ("not configured"), not 403 — proving the filter let the request through.
        mockMvc.perform(post("/api/catalog/sync/push")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isServiceUnavailable());
    }
}
