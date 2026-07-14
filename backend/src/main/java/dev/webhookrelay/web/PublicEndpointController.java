package dev.webhookrelay.web;

import dev.webhookrelay.service.EndpointService;
import dev.webhookrelay.repository.RelayRequestRepository;
import dev.webhookrelay.web.dto.RelayRequestView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only, anonymous access to an endpoint's captured requests. The slug is the
 * capability secret (unguessable, auto-expiring), mirroring the anonymous WebSocket
 * subscription model: anyone with the link can view the live feed and history.
 *
 * This intentionally does NOT require ownership — it powers the "share link" feature.
 * Writes (create/replay) remain owner-scoped in {@link EndpointController}.
 */
@RestController
@RequestMapping("/api/public/endpoints")
@RequiredArgsConstructor
public class PublicEndpointController {

    private final EndpointService endpointService;
    private final RelayRequestRepository relayRequestRepository;

    @GetMapping("/{slug}/requests")
    public ResponseEntity<List<RelayRequestView>> publicRequests(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (endpointService.findActiveBySlug(slug).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        List<RelayRequestView> views = relayRequestRepository
                .findByEndpointSlugOrderByReceivedAtDesc(slug, PageRequest.of(page, Math.min(size, 200)))
                .map(RelayRequestView::of)
                .getContent();
        return ResponseEntity.ok(views);
    }
}
