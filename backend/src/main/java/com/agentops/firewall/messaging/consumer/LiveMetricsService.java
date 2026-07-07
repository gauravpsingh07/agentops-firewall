package com.agentops.firewall.messaging.consumer;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory projection built from the Kafka action-lifecycle stream and the
 * RabbitMQ approval work queue. It proves the consumer side of the messaging
 * architecture actually does something: broker events increment per-type
 * counters and land in a bounded recent-events buffer.
 *
 * <p>Subscribers (e.g. the SSE endpoint) can register a callback to receive
 * each event as it is recorded, turning the consumers into a live feed.
 */
@Service
public class LiveMetricsService {

    private static final int MAX_RECENT = 50;

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<LiveEvent> recent = new ConcurrentLinkedDeque<>();
    private final List<Consumer<LiveEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Record one consumed broker event. Thread-safe; called from listener threads. */
    public LiveEvent record(String source, String type, String summary) {
        counters.computeIfAbsent(type, k -> new AtomicLong()).incrementAndGet();
        LiveEvent event = new LiveEvent(sequence.incrementAndGet(), source, type, summary, Instant.now());
        recent.addFirst(event);
        while (recent.size() > MAX_RECENT) {
            recent.pollLast();
        }
        for (Consumer<LiveEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException ignored) {
                // A slow/broken subscriber must never break metric recording.
            }
        }
        return event;
    }

    /** Snapshot of per-event-type counts (ordered by type name). */
    public Map<String, Long> counts() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> snapshot.put(e.getKey(), e.getValue().get()));
        return snapshot;
    }

    /** Most-recent-first view of the recent events buffer. */
    public List<LiveEvent> recent() {
        return new ArrayList<>(recent);
    }

    public void subscribe(Consumer<LiveEvent> subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(Consumer<LiveEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * A single consumed event.
     *
     * @param source origin channel ({@code kafka} / {@code rabbitmq}).
     * @param type   event/task type (e.g. {@code ACTION_DECIDED}).
     */
    public record LiveEvent(long id, String source, String type, String summary, Instant at) {
    }
}
