import { Injectable, signal } from '@angular/core';

/** Minimal transient-message toast. The message survives a route change so a
 *  page can show it right after navigating. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;

  show(message: string, durationMs = 2500): void {
    this.message.set(message);
    if (this.timer) {
      clearTimeout(this.timer);
    }
    this.timer = setTimeout(() => this.message.set(null), durationMs);
  }
}
