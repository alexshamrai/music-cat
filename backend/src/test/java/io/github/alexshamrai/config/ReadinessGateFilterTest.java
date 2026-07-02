package io.github.alexshamrai.config;

import io.github.alexshamrai.startup.ReadinessState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadinessGateFilterTest {

    @Test
    void notReady_returns503_doesNotContinueChain() throws Exception {
        ReadinessState readinessState = new ReadinessState();
        ReadinessGateFilter filter = new ReadinessGateFilter(readinessState);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verifyNoInteractions(chain);
    }

    @Test
    void ready_continuesChain() throws Exception {
        ReadinessState readinessState = new ReadinessState();
        readinessState.markReady();
        ReadinessGateFilter filter = new ReadinessGateFilter(readinessState);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
