import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { EventStreamService } from './event-stream.service';
import { TokenStorageService } from './token-storage.service';

/** Minimal stand-in for the browser EventSource. */
class FakeEventSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private readonly listeners: Record<string, ((event: MessageEvent) => void)[]> = {};

  constructor(public readonly url: string) {}

  addEventListener(type: string, cb: (event: MessageEvent) => void): void {
    (this.listeners[type] ??= []).push(cb);
  }

  close(): void {
    this.closed = true;
  }

  emit(type: string, data: unknown): void {
    (this.listeners[type] ?? []).forEach((cb) => cb({ data: JSON.stringify(data) } as MessageEvent));
  }
}

class TestableEventStreamService extends EventStreamService {
  lastSource?: FakeEventSource;

  protected override createEventSource(url: string): EventSource {
    this.lastSource = new FakeEventSource(url);
    return this.lastSource as unknown as EventSource;
  }
}

describe('EventStreamService', () => {
  let service: TestableEventStreamService;
  let tokens: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TestableEventStreamService, TokenStorageService]
    });
    service = TestBed.inject(TestableEventStreamService);
    tokens = TestBed.inject(TokenStorageService);
    tokens.clear();
  });

  afterEach(() => {
    tokens.clear();
  });

  it('does not connect when no token is present', () => {
    service.connect();
    expect(service.lastSource).toBeUndefined();
  });

  it('connects to /stream with the token as a query param', () => {
    tokens.setToken('jwt-abc');
    service.connect();
    expect(service.lastSource?.url).toBe(
      `${environment.apiBaseUrl}/stream?access_token=jwt-abc`
    );
  });

  it('emits parsed snapshot and activity events', () => {
    tokens.setToken('jwt-abc');
    const snapshots: unknown[] = [];
    const activity: unknown[] = [];
    service.snapshot$.subscribe((s) => snapshots.push(s));
    service.events$.subscribe((e) => activity.push(e));

    service.connect();
    service.lastSource!.emit('snapshot', { counts: { ACTION_DECIDED: 2 }, recent: [] });
    service.lastSource!.emit('activity', { id: 1, source: 'kafka', type: 'ACTION_DECIDED', summary: 'x', at: 'now' });

    expect(snapshots).toEqual([{ counts: { ACTION_DECIDED: 2 }, recent: [] }]);
    expect(activity).toEqual([{ id: 1, source: 'kafka', type: 'ACTION_DECIDED', summary: 'x', at: 'now' }]);
  });

  it('tracks connection state and closes on disconnect', () => {
    tokens.setToken('jwt-abc');
    service.connect();

    service.lastSource!.onopen?.();
    expect(service.connected()).toBeTrue();

    const source = service.lastSource!;
    service.disconnect();
    expect(source.closed).toBeTrue();
    expect(service.connected()).toBeFalse();
  });

  it('is a no-op to connect twice', () => {
    tokens.setToken('jwt-abc');
    service.connect();
    const first = service.lastSource;
    service.connect();
    expect(service.lastSource).toBe(first);
  });
});
