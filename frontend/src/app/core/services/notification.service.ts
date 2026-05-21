import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'info' | 'success' | 'error';

export interface Notification {
  id: number;
  kind: NotificationKind;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  private readonly _notifications = signal<Notification[]>([]);
  readonly notifications = this._notifications.asReadonly();

  show(kind: NotificationKind, message: string, autoDismissMs = 4000): void {
    const id = this.nextId++;
    this._notifications.update((list) => [...list, { id, kind, message }]);
    if (autoDismissMs > 0) {
      setTimeout(() => this.dismiss(id), autoDismissMs);
    }
  }

  info(message: string): void {
    this.show('info', message);
  }

  success(message: string): void {
    this.show('success', message);
  }

  error(message: string): void {
    this.show('error', message, 6000);
  }

  dismiss(id: number): void {
    this._notifications.update((list) => list.filter((n) => n.id !== id));
  }
}
