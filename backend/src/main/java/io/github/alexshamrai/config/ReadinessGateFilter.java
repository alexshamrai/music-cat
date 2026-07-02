package io.github.alexshamrai.config;

import io.github.alexshamrai.startup.ReadinessState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests with 503 until {@link io.github.alexshamrai.startup.CatalogAutoImporter}
 * has finished its boot decision. On Cloud Run the embedded server starts accepting
 * connections during Spring context refresh, strictly before {@code ApplicationReadyEvent}
 * runs the Sheets restore/seed — without this gate, a request in that window could both see
 * an empty DB and (via the importer's {@code artistRepository.count() > 0} check) trick it
 * into thinking the DB is already populated, skipping the Sheets restore entirely and
 * resuming event-driven pushes over a near-empty DB.
 */
public class ReadinessGateFilter extends OncePerRequestFilter {

    private final ReadinessState readinessState;

    public ReadinessGateFilter(ReadinessState readinessState) {
        this.readinessState = readinessState;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!readinessState.isReady()) {
            // setStatus + direct body write, NOT sendError — see RequireXhrHeaderFilter for
            // why sendError's container-level /error forward re-enters this same filter chain
            // and produces the wrong status on a real server.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "5");
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":503,\"message\":\"Application is starting up\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
