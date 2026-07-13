package dev.webhookrelay.repository;

import dev.webhookrelay.domain.RelayRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelayRequestRepository extends JpaRepository<RelayRequest, String> {
    Page<RelayRequest> findByEndpointSlugOrderByReceivedAtDesc(String endpointSlug, Pageable pageable);
}
