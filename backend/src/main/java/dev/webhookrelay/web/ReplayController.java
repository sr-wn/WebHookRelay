package dev.webhookrelay.web;

import dev.webhookrelay.domain.RelayRequest;
import dev.webhookrelay.repository.RelayRequestRepository;
import dev.webhookrelay.service.EndpointService;
import dev.webhookrelay.service.ReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Replay / diff of captured requests. Owner-scoped: a token may only act on
 * requests captured by endpoints it owns. Unknown request ids are 404; requests
 * that belong to another owner (resolved via the parent endpoint) are 403.
 */
@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;
    private final EndpointService endpointService;
    private final RelayRequestRepository relayRequestRepository;

    public record ReplayRequestBody(String requestId, String targetUrl) {}
    public record DiffRequestBody(String idA, String idB) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> replay(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestBody ReplayRequestBody body) {
        ResponseEntity<Void> denied = assertOwned(body.requestId(), jwt.getSubject());
        if (denied != null) {
            return ResponseEntity.status(denied.getStatusCode()).build();
        }
        return ResponseEntity.ok(replayService.replay(body.requestId(), body.targetUrl()));
    }

    @PostMapping("/diff")
    public ResponseEntity<Map<String, Object>> diff(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestBody DiffRequestBody body) {
        ResponseEntity<Void> deniedA = assertOwned(body.idA(), jwt.getSubject());
        if (deniedA != null) {
            return ResponseEntity.status(deniedA.getStatusCode()).build();
        }
        ResponseEntity<Void> deniedB = assertOwned(body.idB(), jwt.getSubject());
        if (deniedB != null) {
            return ResponseEntity.status(deniedB.getStatusCode()).build();
        }
        return ResponseEntity.ok(replayService.diff(body.idA(), body.idB()));
    }

    /** Returns a 404/403 response if the request is not owned by {@code ownerId}, else null. */
    private ResponseEntity<Void> assertOwned(String requestId, String ownerId) {
        RelayRequest request = relayRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        HttpStatus status = endpointService.ownershipStatus(request.getEndpointSlug(), ownerId);
        return status == null ? null : ResponseEntity.status(status).build();
    }
}
