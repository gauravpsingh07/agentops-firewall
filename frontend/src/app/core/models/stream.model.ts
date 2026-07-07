/** A single live activity event pushed over the SSE stream. */
export interface LiveActivityEvent {
  id: number;
  source: string;
  type: string;
  summary: string;
  at: string;
}

/** The initial snapshot event sent when the stream connects. */
export interface StreamSnapshot {
  counts: Record<string, number>;
  recent: LiveActivityEvent[];
}
