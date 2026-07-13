package dev.webhookrelay.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exposes the number of currently-connected STOMP WebSocket sessions as a Micrometer
 * gauge ({@code webhookrelay.ws.sessions.active}), so the live dashboard can show real
 * fan-out load. Counts are per-instance; sum across instances in Grafana.
 */
@Component
public class WebSocketSessionMetrics {

    private final AtomicInteger active;

    public WebSocketSessionMetrics(MeterRegistry registry) {
        this.active = registry.gauge("webhookrelay.ws.sessions.active", new AtomicInteger(0));
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        active.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        active.decrementAndGet();
    }
}
