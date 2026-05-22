import { fakeAsync, TestBed, tick } from '@angular/core/testing';

import { NotificationService } from './notification.service';

/**
 * The notification service backs the global toast region surfaced
 * via NotificationsComponent. State is held in a signal, which is
 * read directly by the component's template — any regression here
 * (toasts not appearing, not disappearing, or sticking around across
 * navigations) breaks visible UX, so we cover the enqueue / dismiss
 * lifecycle and the auto-dismiss timer end-to-end.
 */
describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(NotificationService);
  });

  it('starts with an empty notification list', () => {
    expect(service.notifications()).toEqual([]);
  });

  it('show() enqueues a notification with the given kind and message', () => {
    service.show('info', 'hello world', /* no auto-dismiss */ 0);

    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0]).toEqual(
      jasmine.objectContaining({ kind: 'info', message: 'hello world' })
    );
  });

  it('assigns each notification a unique, monotonically increasing id', () => {
    service.show('info', 'first', 0);
    service.show('info', 'second', 0);
    service.show('info', 'third', 0);

    const ids = service.notifications().map((n) => n.id);
    expect(new Set(ids).size).toBe(3);
    expect(ids[1]).toBeGreaterThan(ids[0]);
    expect(ids[2]).toBeGreaterThan(ids[1]);
  });

  it('info() / success() / error() helpers route through show() with the right kind', () => {
    service.info('an info message');
    service.success('a success message');
    service.error('an error message');

    const kinds = service.notifications().map((n) => n.kind);
    expect(kinds).toEqual(['info', 'success', 'error']);
  });

  it('dismiss() removes the notification with the matching id and leaves siblings', () => {
    service.show('info', 'first', 0);
    service.show('info', 'second', 0);
    const [first, second] = service.notifications();

    service.dismiss(first.id);

    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0].id).toBe(second.id);
  });

  it('auto-dismisses after the configured timeout', fakeAsync(() => {
    service.show('info', 'fleeting', /* autoDismissMs */ 4000);
    expect(service.notifications().length).toBe(1);

    tick(3999);
    expect(service.notifications().length).toBe(1);

    tick(1);
    expect(service.notifications().length).toBe(0);
  }));

  it('error() uses a longer (6 s) auto-dismiss than info()/success()', fakeAsync(() => {
    service.error('something went wrong');
    expect(service.notifications().length).toBe(1);

    // Past the info/success 4 s default but before the 6 s error default.
    tick(4500);
    expect(service.notifications().length).toBe(1);

    tick(1500);
    expect(service.notifications().length).toBe(0);
  }));

  it('autoDismissMs = 0 keeps the notification until explicitly dismissed', fakeAsync(() => {
    service.show('info', 'sticky', 0);

    tick(60_000);
    expect(service.notifications().length).toBe(1);

    service.dismiss(service.notifications()[0].id);
    expect(service.notifications().length).toBe(0);
  }));
});
