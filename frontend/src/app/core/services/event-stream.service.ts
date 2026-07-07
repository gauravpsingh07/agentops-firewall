import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { LiveActivityEvent, StreamSnapshot } from '../models/stream.model';
import { TokenStorageService } from './token-storage.service';

/**
 * Wraps a browser `EventSource` connected to the backend SSE feed
 * (`GET /api/stream`). Since `EventSource` cannot set an Authorization
 * header, the JWT is passed as the `access_token` query parameter.
 *
 * Consumers subscribe to {@link events$} for live activity and
 * {@link snapshot$} for the initial state, and read {@link connected} for
 * connection status.
 */
@Injectable({ providedIn: 'root' })
export class EventStreamService {
  private readonly tokens = inject(TokenStorageService);

  private source?: EventSource;

  private readonly eventsSubject = new Subject<LiveActivityEvent>();
  readonly events$ = this.eventsSubject.asObservable();

  private readonly snapshotSubject = new Subject<StreamSnapshot>();
  readonly snapshot$ = this.snapshotSubject.asObservable();

  private readonly connectedSignal = signal(false);
  readonly connected = this.connectedSignal.asReadonly();

  /** Open the stream if not already open and a token is present. */
  connect(): void {
    if (this.source) {
      return;
    }
    const token = this.tokens.getToken();
    if (!token) {
      return;
    }
    const url = `${environment.apiBaseUrl}/stream?access_token=${encodeURIComponent(token)}`;
    const source = this.createEventSource(url);

    source.addEventListener('snapshot', (event) =>
      this.emit(this.snapshotSubject, event as MessageEvent)
    );
    source.addEventListener('activity', (event) =>
      this.emit(this.eventsSubject, event as MessageEvent)
    );
    source.onopen = () => this.connectedSignal.set(true);
    source.onerror = () => this.connectedSignal.set(false);

    this.source = source;
  }

  /** Close the stream and reset connection state. */
  disconnect(): void {
    this.source?.close();
    this.source = undefined;
    this.connectedSignal.set(false);
  }

  /** Overridable seam so unit tests can inject a fake EventSource. */
  protected createEventSource(url: string): EventSource {
    return new EventSource(url);
  }

  private emit<T>(subject: Subject<T>, event: MessageEvent): void {
    try {
      subject.next(JSON.parse(event.data) as T);
    } catch {
      // Ignore malformed frames rather than tearing down the stream.
    }
  }
}
