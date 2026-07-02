package io.github.alexshamrai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Blocks blind cross-site form submissions to state-changing endpoints. HTTP Basic
 * credentials are cached per-origin by the browser and auto-attached to any request to
 * that origin regardless of which page initiated it, so a plain
 * {@code <form method="POST">} on any other site could otherwise trigger a mutation here —
 * most severely POST /api/catalog/sync/pull (wipes and rebuilds the whole DB) and
 * /sync/push (overwrites the Google Sheet source of truth), since both take no request body
 * and so have no content-type gate. Plain HTML forms cannot set custom headers, so requiring
 * one blocks blind form-based CSRF without needing CSRF-token issuance/refresh machinery.
 */
public class RequireXhrHeaderFilter extends OncePerRequestFilter {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String REQUIRED_HEADER = "X-Requested-With";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (STATE_CHANGING_METHODS.contains(request.getMethod()) && request.getHeader(REQUIRED_HEADER) == null) {
            // setStatus + direct body write, NOT sendError: Spring Boot's security filter
            // chain also runs on ERROR-dispatch forwards, so sendError() re-enters this same
            // filter chain via the /error forward and its outcome depends on re-authenticating
            // that second pass — on a real server this produced a confusing 401 instead of
            // the intended 403 (not caught by MockMvc, which doesn't replicate container-level
            // error-page forwarding).
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":403,\"message\":\"Missing " + REQUIRED_HEADER + " header\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
