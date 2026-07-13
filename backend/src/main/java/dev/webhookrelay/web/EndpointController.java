package dev.webhookrelay.web;

import dev.webhookrelay.service.EndpointService;
import dev.webhookrelay.repository.RelayRequestRepository;
import dev.webhookrelay.web.dto.EndpointView;
import dev.webhookrelay.web.dto.RelayRequestView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Management API (CORS-restricted, JWT-required). Every endpoint is owned by the
 * token's {@code sub} (ownerId). A caller may only read endpoints they own; a slug
 * that exists but belongs to someone else returns 403, while an unknown/expired slug
 * returns 404.
 *
 * The public capture path (/relay/{slug}) is intentionally NOT here and stays anonymous.
 */
@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointService endpointService;
    private final RelayRequestRepository relayRequestRepository;

    @PostMapping
    public ResponseEntity<EndpointView> create(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EndpointView.of(endpointService.create(jwt.getSubject())));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<EndpointView> get(@PathVariable String slug,
                                            @AuthenticationPrincipal Jwt jwt) {
        return endpointService.findActiveBySlugForOwner(slug, jwt.getSubject())
                .map(e -> ResponseEntity.ok(EndpointView.of(e)))
                .orElse(ownershipResponse(slug, jwt.getSubject()));
    }

    @GetMapping("/{slug}/requests")
    public ResponseEntity<List<RelayRequestView>> requests(
            @PathVariable String slug,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (endpointService.findActiveBySlugForOwner(slug, jwt.getSubject()).isEmpty()) {
            return ownershipResponse(slug, jwt.getSubject());
        }
        List<RelayRequestView> views = relayRequestRepository
                .findByEndpointSlugOrderByReceivedAtDesc(slug, PageRequest.of(page, Math.min(size, 200)))
                .map(RelayRequestView::of)
                .getContent();
        return ResponseEntity.ok(views);
    }

    /** 404 if unknown/expired; 403 if it exists but is owned by a different token. */
    private <T> ResponseEntity<T> ownershipResponse(String slug, String ownerId) {
        if (endpointService.findActiveBySlug(slug).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
