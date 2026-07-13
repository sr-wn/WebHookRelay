package dev.webhookrelay.web;

import dev.webhookrelay.service.CaptureService;
import dev.webhookrelay.service.EndpointService;
import dev.webhookrelay.service.RateLimiter;
import dev.webhookrelay.web.dto.RelayRequestView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public capture endpoint. Accepts ANY HTTP method on /relay/{slug}.
 * Rate limited per-slug and per-IP. Always returns 200 quickly (fire-and-inspect).
 */
@RestController
@RequiredArgsConstructor
public class RelayController {

    private static final Logger log = LoggerFactory.getLogger(RelayController.class);

    private final EndpointService endpointService;
    private final CaptureService captureService;
    private final RateLimiter rateLimiter;

    @RequestMapping(
            value = "/relay/{slug}",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<?> capture(@PathVariable String slug,
                                     @RequestBody(required = false) byte[] body,
                                     HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!rateLimiter.allowIp(ip) || !rateLimiter.allowSlug(slug)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "rate_limited"));
        }

        if (endpointService.findActiveBySlug(slug).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "endpoint_not_found_or_expired"));
        }

        RelayRequestView view = captureService.capture(slug, request, body);
        log.info("Captured request slug={} method={} id={}", slug, request.getMethod(), view.id());
        return ResponseEntity.ok(Map.of("status", "captured", "id", view.id()));
    }
}
