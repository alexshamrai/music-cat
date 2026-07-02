package io.github.alexshamrai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpaForwardingController.class)
@ActiveProfiles("test")
@io.github.alexshamrai.WithAuthenticatedUser
class SpaForwardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/browse", "/artists", "/artists/42", "/albums", "/albums/42",
            "/random", "/favorites", "/tags"
    })
    void spaRoutes_forwardToIndexHtml(String route) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void nonNumericDetailPath_isNotForwarded() throws Exception {
        mockMvc.perform(get("/albums/not-a-number"))
                .andExpect(status().isNotFound());
    }
}
