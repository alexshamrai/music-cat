package io.github.alexshamrai.sheets;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.Sheets;
import io.github.alexshamrai.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Layer 2: exercises GoogleSheetsClient's real request/response handling and 429 retry via
 * Google's MockHttpTransport — the executeWithRetry path the deep-stub tests bypass.
 */
class GoogleSheetsClientRetryTest {

    private GoogleSheetsClient clientBackedBy(MockHttpTransport transport) {
        Sheets sheets = new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), request -> {})
                .setApplicationName("music-cat-test")
                .build();
        @SuppressWarnings("unchecked")
        ObjectProvider<Sheets> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sheets);
        return new GoogleSheetsClient(provider, new SheetsProperties(true, "creds.json", "sheet-123"));
    }

    @Test
    void read_retriesAfter429_thenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest() {
                    @Override
                    public LowLevelHttpResponse execute() {
                        if (calls.getAndIncrement() == 0) {
                            return new MockLowLevelHttpResponse()
                                    .setStatusCode(429)
                                    .setContentType("application/json")
                                    .setContent("{\"error\":{\"code\":429,\"message\":\"Rate Limit Exceeded\"}}");
                        }
                        return new MockLowLevelHttpResponse()
                                .setStatusCode(200)
                                .setContentType("application/json")
                                .setContent("{\"values\":[[\"name\"],[\"Pink Floyd\"]]}");
                    }
                };
            }
        };

        List<List<Object>> rows = clientBackedBy(transport).read("Artists");

        assertThat(calls.get()).isEqualTo(2); // one 429 + one success
        assertThat(rows).containsExactly(List.of("name"), List.of("Pink Floyd"));
    }

    @Test
    void read_non429Error_failsImmediatelyWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest() {
                    @Override
                    public LowLevelHttpResponse execute() {
                        calls.incrementAndGet();
                        return new MockLowLevelHttpResponse()
                                .setStatusCode(500)
                                .setContentType("application/json")
                                .setContent("{\"error\":{\"code\":500,\"message\":\"Backend Error\"}}");
                    }
                };
            }
        };

        assertThatThrownBy(() -> clientBackedBy(transport).read("Artists"))
                .isInstanceOf(RuntimeException.class);
        assertThat(calls.get()).isEqualTo(1); // no retry on a non-429 error
    }
}
