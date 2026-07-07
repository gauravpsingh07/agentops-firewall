package com.agentops.firewall.stream;

import com.agentops.firewall.messaging.consumer.LiveMetricsService.LiveEvent;

import java.util.List;
import java.util.Map;

/**
 * The initial {@code snapshot} SSE event sent to a newly connected client:
 * the current per-type counters and the recent-events buffer, so the UI can
 * render immediately before the first live {@code activity} event arrives.
 */
public record StreamSnapshot(Map<String, Long> counts, List<LiveEvent> recent) {
}
