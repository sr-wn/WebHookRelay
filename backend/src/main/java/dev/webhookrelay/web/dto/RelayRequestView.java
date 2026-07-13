package dev.webhookrelay.web.dto;

import dev.webhookrelay.domain.RelayRequest;

import java.time.Instant;
import java.util.Map;

public record RelayRequestView(
        String id,
        String endpointSlug,
        String method,
        Map<String, Object> headers,
        Map<String, Object> queryParams,
        String bodyContentType,
        String body,
        boolean bodyTruncated,
        String sourceIp,
        Instant receivedAt
) {
    public static RelayRequestView of(RelayRequest r) {
        return new RelayRequestView(
                r.getId(), r.getEndpointSlug(), r.getMethod(), r.getHeaders(),
                r.getQueryParams(), r.getBodyContentType(), r.getBody(),
                r.isBodyTruncated(), r.getSourceIp(), r.getReceivedAt());
    }
}
