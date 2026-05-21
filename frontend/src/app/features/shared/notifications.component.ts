import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { NotificationService } from '../../core/services/notification.service';

/**
 * Stack of transient toast notifications. Reads from NotificationService
 * (which exposes a signal of active notifications) and renders them in a
 * fixed overlay in the top-right corner. Mounted once at the application
 * root so any component can call notify.success/error/info(...) without
 * needing access to the DOM.
 *
 * The list region is `aria-live="polite"` so a screen reader announces
 * new messages without stealing focus.
 */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="pointer-events-none fixed top-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2"
      role="region"
      aria-live="polite"
      aria-label="Notifications"
    >
      @for (note of notify.notifications(); track note.id) {
        <div
          class="pointer-events-auto rounded-md border px-3 py-2 text-sm shadow-lg backdrop-blur"
          [class]="toneClass(note.kind)"
        >
          <div class="flex items-start gap-2">
            <span class="flex-1">{{ note.message }}</span>
            <button
              type="button"
              class="text-xs opacity-70 hover:opacity-100"
              (click)="notify.dismiss(note.id)"
              aria-label="Dismiss notification"
            >✕</button>
          </div>
        </div>
      }
    </div>
  `
})
export class NotificationsComponent {
  readonly notify = inject(NotificationService);

  toneClass(kind: 'info' | 'success' | 'error'): string {
    switch (kind) {
      case 'success':
        return 'border-emerald-700/40 bg-emerald-900/80 text-emerald-100';
      case 'error':
        return 'border-red-700/40 bg-red-900/80 text-red-100';
      default:
        return 'border-slate-700 bg-slate-900/80 text-slate-100';
    }
  }
}
