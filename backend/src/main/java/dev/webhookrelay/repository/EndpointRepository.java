package dev.webhookrelay.repository;

import dev.webhookrelay.domain.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EndpointRepository extends JpaRepository<Endpoint, String> {

    Optional<Endpoint> findBySlug(String slug);

    @Modifying
    @Query("delete from Endpoint e where e.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
