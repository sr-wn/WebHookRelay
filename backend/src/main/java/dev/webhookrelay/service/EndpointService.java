package dev.webhookrelay.service;

import dev.webhookrelay.config.WebhookRelayProperties;
import dev.webhookrelay.domain.Endpoint;
import dev.webhookrelay.repository.EndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndpointService {

    private static final Logger log = LoggerFactory.getLogger(EndpointService.class);

    private final EndpointRepository repository;
    private final SlugGenerator slugGenerator;
    private final WebhookRelayProperties props;
    private final RedisEndpointCache endpointCache;

    @Transactional
    public Endpoint create(String ownerId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Endpoint e = new Endpoint();
            e.setId(UUID.randomUUID().toString());
            e.setSlug(slugGenerator.generate());
            e.setOwnerId(ownerId);
            e.setCreatedAt(Instant.now());
            e.setExpiresAt(Instant.now().plus(props.defaultTtl()));
            try {
                Endpoint saved = repository.saveAndFlush(e);
                // Make the new endpoint immediately capturable — don't wait out the TTL.
                endpointCache.evict(saved.getSlug());
                return saved;
            } catch (DataIntegrityViolationException collision) {
                log.warn("Slug collision on attempt {}, retrying", attempt);
            }
        }
        throw new IllegalStateException("Could not generate a unique slug after retries");
    }

    /**
     * Hot-path lookup used on every capture. Served from a short-TTL Redis cache; on a
     * miss (or a cached entry that has since expired) we fall through to MySQL, then
     * refresh the cache. The local expiry check guards against serving a stale entry.
     */
    @Transactional(readOnly = true)
    public Optional<Endpoint> findActiveBySlug(String slug) {
        Instant now = Instant.now();
        Optional<Endpoint> cached = endpointCache.get(slug);
        if (cached != null && RedisEndpointCache.isStillActive(cached, now)) {
            return cached;
        }
        Optional<Endpoint> fromDb = repository.findBySlug(slug)
                .filter(e -> !e.isExpired(now));
        endpointCache.put(slug, fromDb, props.endpointCacheTtl());
        return fromDb;
    }

    /** Active endpoint only if it belongs to {@code ownerId}; basis for ownership enforcement.
     *  Reuses the cached active lookup so ownership checks stay on the hot path too. */
    @Transactional(readOnly = true)
    public Optional<Endpoint> findActiveBySlugForOwner(String slug, String ownerId) {
        return findActiveBySlug(slug)
                .filter(e -> ownerId.equals(e.getOwnerId()));
    }

    /**
     * Ownership verdict for a slug relative to {@code ownerId}, shared by every
     * management path so they agree: {@code null} if the caller owns the (active)
     * endpoint, 404 if the slug is unknown/expired, 403 if it exists but belongs to
     * another owner.
     */
    @Transactional(readOnly = true)
    public HttpStatus ownershipStatus(String slug, String ownerId) {
        if (findActiveBySlugForOwner(slug, ownerId).isPresent()) {
            return null;
        }
        return findActiveBySlug(slug).isEmpty() ? HttpStatus.NOT_FOUND : HttpStatus.FORBIDDEN;
    }

    /** TTL sweeper. Runs on every instance; deleteExpired is idempotent/safe under contention. */
    @Scheduled(fixedDelayString = "${webhookrelay.expiry-sweep-interval-ms:60000}")
    @Transactional
    public void sweepExpired() {
        int removed = repository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Expiry sweep removed {} endpoints", removed);
        }
    }
}
