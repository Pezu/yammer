import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Web Push for the waiter PWA: registers the service worker, asks for notification permission,
 * subscribes via the Push API, and syncs the subscription with the server. Pushes are delivered by
 * the OS (FCM/APNs) and shown by the service worker even when the app is closed.
 *
 * iOS only delivers push when the app is installed to the Home Screen (iOS 16.4+).
 */
@Injectable({ providedIn: 'root' })
export class WaiterPushService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/push`;

  readonly supported =
    'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  readonly permission = signal<NotificationPermission>(
    this.supported ? Notification.permission : 'denied',
  );
  readonly subscribed = signal(false);
  readonly busy = signal(false);

  /** Reflect the current SW subscription state (call on app load). */
  async refresh(): Promise<void> {
    if (!this.supported) return;
    this.permission.set(Notification.permission);
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      const sub = reg ? await reg.pushManager.getSubscription() : null;
      this.subscribed.set(!!sub);
    } catch {
      this.subscribed.set(false);
    }
  }

  /** Register the SW, request permission, subscribe, and send the subscription to the server. */
  async enable(): Promise<boolean> {
    if (!this.supported || this.busy()) return false;
    this.busy.set(true);
    try {
      const reg = await navigator.serviceWorker.register('/sw.js');
      const permission = await Notification.requestPermission();
      this.permission.set(permission);
      if (permission !== 'granted') return false;

      const { publicKey } = await firstValueFrom(
        this.http.get<{ publicKey: string }>(`${this.base}/public-key`),
      );
      if (!publicKey) return false;

      let sub = await reg.pushManager.getSubscription();
      if (!sub) {
        sub = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: this.urlBase64ToUint8Array(publicKey) as BufferSource,
        });
      }
      await firstValueFrom(this.http.post(`${this.base}/subscribe`, sub.toJSON()));
      this.subscribed.set(true);
      return true;
    } catch {
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  /** Unsubscribe locally and on the server. */
  async disable(): Promise<void> {
    if (!this.supported) return;
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      const sub = reg ? await reg.pushManager.getSubscription() : null;
      if (sub) {
        await firstValueFrom(this.http.post(`${this.base}/unsubscribe`, { endpoint: sub.endpoint }));
        await sub.unsubscribe();
      }
      this.subscribed.set(false);
    } catch {
      /* ignore */
    }
  }

  private urlBase64ToUint8Array(base64: string): Uint8Array {
    const padding = '='.repeat((4 - (base64.length % 4)) % 4);
    const b64 = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
    const raw = atob(b64);
    const out = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) {
      out[i] = raw.charCodeAt(i);
    }
    return out;
  }
}
