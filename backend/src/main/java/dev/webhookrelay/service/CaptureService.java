package dev.webhookrelay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhookrelay.config.WebhookRelayProperties;
import dev.webhookrelay.domain.RelayRequest;
import dev.webhookrelay.relay.RedisEventRelay;
import dev.webhookrelay.repository.RelayRequestRepository;
import dev.webhookrelay.web.dto.RelayRequestView;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CaptureService {

    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    private final RelayRequestRepository repository;
    private final RedisEventRelay relay;
    private final WebhookRelayProperties props;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Persist a captured request then broadcast it via Redis so ALL instances'
     * WebSocket clients receive it. Body is capped/truncated to prevent OOM abuse.
     */
    @Transactional
    public RelayRequestView capture(String slug, HttpServletRequest req, byte[] rawBody) {
        // Measures end-to-end receipt -> broadcast latency. This is the SLO metric:
        // alert if p99 of webhookrelay.capture.latency exceeds 500ms (see docs + alert rules).
        long startNanos = System.nanoTime();

        RelayRequest r = new RelayRequest();
        r.setId(UUID.randomUUID().toString());
        r.setEndpointSlug(slug);
        r.setMethod(req.getMethod());
        r.setHeaders(extractHeaders(req));
        r.setQueryParams(extractQueryParams(req));
        r.setBodyContentType(req.getContentType());
        r.setSourceIp(clientIp(req));
        r.setReceivedAt(Instant.now());

        byte[] body = rawBody == null ? new byte[0] : rawBody;
        if (body.length > props.maxBodyBytes()) {
            byte[] capped = new byte[props.maxBodyBytes()];
            System.arraycopy(body, 0, capped, 0, props.maxBodyBytes());
            r.setBody(new String(capped, StandardCharsets.UTF_8));
            r.setBodyTruncated(true);
        } else {
            r.setBody(new String(body, StandardCharsets.UTF_8));
            r.setBodyTruncated(false);
        }

        repository.save(r);

        RelayRequestView view = RelayRequestView.of(r);
        try {
            relay.publish(slug, objectMapper.writeValueAsString(view));
        } catch (Exception e) {
            log.error("Failed to publish captured event slug={} id={}", slug, r.getId(), e);
        }

        Timer.builder("webhookrelay.capture.latency")
                .description("End-to-end latency from webhook receipt to Redis broadcast")
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);

        meterRegistry.counter("webhookrelay.capture.total", "method", r.getMethod()).increment();
        return view;
    }

    private Map<String, Object> extractHeaders(HttpServletRequest req) {
        Map<String, Object> headers = new HashMap<>();
        for (String name : Collections.list(req.getHeaderNames())) {
            headers.put(name, req.getHeader(name));
        }
        return headers;
    }

    private Map<String, Object> extractQueryParams(HttpServletRequest req) {
        Map<String, Object> params = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> params.put(k, v.length == 1 ? v[0] : v));
        return params;
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
