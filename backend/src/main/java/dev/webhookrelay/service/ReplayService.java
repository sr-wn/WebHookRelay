package dev.webhookrelay.service;

import dev.webhookrelay.domain.RelayRequest;
import dev.webhookrelay.repository.RelayRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReplayService {

    private final RelayRequestRepository repository;
    private final WebClient.Builder webClientBuilder;

    /** Re-send a stored request to an arbitrary target URL and return the response summary. */
    public Map<String, Object> replay(String requestId, String targetUrl) {
        RelayRequest r = repository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("request not found: " + requestId));

        WebClient client = webClientBuilder.build();
        ResponseEntity<String> response = client
                .method(HttpMethod.valueOf(r.getMethod()))
                .uri(targetUrl)
                .headers(h -> {
                    if (r.getHeaders() != null) {
                        r.getHeaders().forEach((k, v) -> {
                            if (isForwardable(k)) h.add(k, String.valueOf(v));
                        });
                    }
                })
                .bodyValue(r.getBody() == null ? "" : r.getBody())
                .retrieve()
                .toEntity(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targetUrl", targetUrl);
        out.put("status", response == null ? null : response.getStatusCode().value());
        out.put("responseBody", response == null ? null : response.getBody());
        return out;
    }

    /** Shallow diff of two captured requests (method, headers, body). */
    public Map<String, Object> diff(String idA, String idB) {
        RelayRequest a = repository.findById(idA)
                .orElseThrow(() -> new NoSuchElementException("request not found: " + idA));
        RelayRequest b = repository.findById(idB)
                .orElseThrow(() -> new NoSuchElementException("request not found: " + idB));

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("methodChanged", !Objects.equals(a.getMethod(), b.getMethod()));
        diff.put("bodyChanged", !Objects.equals(a.getBody(), b.getBody()));
        diff.put("headersA", a.getHeaders());
        diff.put("headersB", b.getHeaders());
        diff.put("bodyA", a.getBody());
        diff.put("bodyB", b.getBody());
        return diff;
    }

    private boolean isForwardable(String header) {
        String h = header.toLowerCase();
        // Don't forward hop-by-hop / host-specific headers.
        return !(h.equals("host") || h.equals("content-length") || h.equals("connection"));
    }
}
