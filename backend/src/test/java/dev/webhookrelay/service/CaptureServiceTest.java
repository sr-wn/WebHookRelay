package dev.webhookrelay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhookrelay.domain.RelayRequest;
import dev.webhookrelay.relay.RedisEventRelay;
import dev.webhookrelay.repository.RelayRequestRepository;
import dev.webhookrelay.config.WebhookRelayProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for request parsing in CaptureService — the logic the spec calls out
 * explicitly (header / query-param / body extraction + size cap). Runs without
 * Testcontainers so it's green anywhere, unlike the integration tests.
 */
@ExtendWith(MockitoExtension.class)
class CaptureServiceTest {

    @Mock RelayRequestRepository repository;
    @Mock RedisEventRelay relay;
    @Mock WebhookRelayProperties props;
    @Mock HttpServletRequest request;
    @Captor ArgumentCaptor<RelayRequest> captor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private CaptureService service;

    @BeforeEach
    void setup() {
        when(props.maxBodyBytes()).thenReturn(1024);
        service = new CaptureService(repository, relay, props, objectMapper, meterRegistry);
    }

    @Test
    void parsesHeadersQueryBodyAndClientIp() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getHeaderNames())
                .thenReturn(Collections.enumeration(List.of("X-Test", "Authorization")));
        when(request.getHeader("X-Test")).thenReturn("abc");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        when(request.getParameterMap()).thenReturn(Map.of("q", new String[]{"1"}));
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");

        service.capture("slug1", request, "{\"a\":1}".getBytes(StandardCharsets.UTF_8));

        verify(repository).save(captor.capture());
        RelayRequest r = captor.getValue();
        assertThat(r.getEndpointSlug()).isEqualTo("slug1");
        assertThat(r.getMethod()).isEqualTo("POST");
        assertThat(r.getHeaders())
                .containsEntry("X-Test", "abc")
                .containsEntry("Authorization", "Bearer x");
        assertThat(r.getQueryParams()).containsEntry("q", "1");
        assertThat(r.getSourceIp()).isEqualTo("203.0.113.7"); // first hop of XFF
        assertThat(r.getBody()).isEqualTo("{\"a\":1}");
        assertThat(r.isBodyTruncated()).isFalse();
    }

    @Test
    void truncatesOversizedBodyToCap() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("text/plain");
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        byte[] big = new byte[2048];
        Arrays.fill(big, (byte) 'x');

        service.capture("s", request, big);

        verify(repository).save(captor.capture());
        RelayRequest r = captor.getValue();
        assertThat(r.getBody().length()).isEqualTo(1024);
        assertThat(r.isBodyTruncated()).isTrue();
    }

    @Test
    void fallsBackToRemoteAddrWhenNoForwardedHeader() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getContentType()).thenReturn(null);
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("198.51.100.9");

        service.capture("s", request, null);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSourceIp()).isEqualTo("198.51.100.9");
    }
}
