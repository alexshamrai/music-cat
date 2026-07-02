package io.github.alexshamrai.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards React Router routes to index.html so deep links (e.g. /albums/42) load the SPA
 * instead of a Whitelabel 404.
 *
 * <p>The mappings below are the explicit list of routes from frontend/src/App.tsx —
 * new frontend routes must be added here.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/browse", "/artists", "/artists/{id:[0-9]+}", "/albums",
                 "/albums/{id:[0-9]+}", "/random", "/favorites", "/tags"})
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
