package com.agentops.firewall.stream;

import com.agentops.firewall.messaging.consumer.LiveMetricsService;
import com.agentops.firewall.messaging.consumer.LiveMetricsService.LiveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Server-Sent Events feed of live firewall activity, sourced from the broker
 * consumers' {@link LiveMetricsService}. On connect the client receives one
 * {@code snapshot} event (current counters + recent buffer), then a
 * {@code activity} event for every subsequent consumed broker event, so the
 * dashboard and approval inbox can update without polling.
 *
 * <p>{@code EventSource} cannot set an Authorization header, so this endpoint
 * also accepts the JWT as an {@code access_token} query parameter (handled in
 * {@code JwtAuthenticationFilter}). Access still requires a valid token.
 */
@RestController
@RequestMapping("/api/stream")
public class LiveStreamController {

    private static final Logger log = LoggerFactory.getLogger(LiveStreamController.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final LiveMetricsService metrics;

    public LiveStreamController(LiveMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(new StreamSnapshot(metrics.counts(), metrics.recent())));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return emitter;
        }

        Consumer<LiveEvent> subscriber = event -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("activity")
                        .id(Long.toString(event.id()))
                        .data(event));
            } catch (IOException | IllegalStateException ex) {
                // Client went away or emitter already completed — stop feeding it.
                emitter.completeWithError(ex);
            }
        };
        metrics.subscribe(subscriber);

        emitter.onCompletion(() -> metrics.unsubscribe(subscriber));
        emitter.onTimeout(() -> {
            metrics.unsubscribe(subscriber);
            emitter.complete();
        });
        emitter.onError(ex -> {
            metrics.unsubscribe(subscriber);
            log.debug("SSE stream error: {}", ex.getMessage());
        });
        return emitter;
    }
}
