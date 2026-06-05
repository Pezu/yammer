import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../../core/auth.service';
import { environment } from '../../../environments/environment';
import { AppLogo } from '../../shared/logo.component';
import { OrderStatus, ServiceOrder, ServiceOrderService } from './service-order.service';

interface Column {
  status: Extract<OrderStatus, 'ORDERED' | 'READY'>;
  label: string;
  color: string;
}

const WS_RECONNECT_MS = 3000;

@Component({
  selector: 'app-service-page',
  imports: [AppLogo],
  templateUrl: './service-page.html',
  styleUrl: './service-page.scss',
})
export class ServicePage implements OnDestroy {
  private readonly service = inject(ServiceOrderService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);

  readonly orders = signal<ServiceOrder[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly dragging = signal<string | null>(null);
  readonly dragOver = signal<string | null>(null);
  readonly fullscreen = signal(false);

  private wakeLock: { release: () => Promise<void> } | null = null;
  private readonly onFsChange = () => {
    const fs = !!document.fullscreenElement;
    this.fullscreen.set(fs);
    if (fs) {
      void this.acquireWakeLock();
    } else {
      void this.releaseWakeLock();
    }
  };
  private readonly onVisibility = () => {
    if (document.visibilityState === 'visible' && document.fullscreenElement) {
      void this.acquireWakeLock();
    }
  };

  readonly username = computed(() => this.auth.session()?.username ?? '');

  readonly columns: Column[] = [
    { status: 'ORDERED', label: 'Ordered', color: '#3b82f6' },
    { status: 'READY', label: 'Ready', color: '#10b981' },
  ];

  private ws: WebSocket | null = null;
  private wsReconnect: ReturnType<typeof setTimeout> | undefined;
  private destroyed = false;

  constructor() {
    this.load(true);
    this.connectWs();
    document.addEventListener('fullscreenchange', this.onFsChange);
    document.addEventListener('visibilitychange', this.onVisibility);
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.wsReconnect) clearTimeout(this.wsReconnect);
    this.ws?.close();
    document.removeEventListener('fullscreenchange', this.onFsChange);
    document.removeEventListener('visibilitychange', this.onVisibility);
    this.ghost?.remove();
    void this.releaseWakeLock();
  }

  // --- live updates over WebSocket ---------------------------------------

  private connectWs(): void {
    const token = this.auth.token;
    if (!token || this.destroyed) return;
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const url = `${proto}://${location.host}${environment.apiUrl}/ws/orders?token=${encodeURIComponent(token)}`;
    try {
      const ws = new WebSocket(url);
      this.ws = ws;
      ws.onopen = () => this.load(false); // resync on (re)connect
      ws.onmessage = () => this.load(false); // ORDER_CREATED / ORDER_DELIVERED → refresh
      ws.onclose = () => {
        if (this.ws === ws) this.ws = null;
        this.scheduleReconnect();
      };
      ws.onerror = () => ws.close();
    } catch {
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.destroyed || this.wsReconnect) return;
    this.wsReconnect = setTimeout(() => {
      this.wsReconnect = undefined;
      this.connectWs();
    }, WS_RECONNECT_MS);
  }

  // --- fullscreen + keep-awake -------------------------------------------

  toggleFullscreen(): void {
    if (document.fullscreenElement) {
      void document.exitFullscreen?.();
    } else {
      void document.documentElement.requestFullscreen?.();
    }
  }

  private async acquireWakeLock(): Promise<void> {
    try {
      const nav = navigator as Navigator & {
        wakeLock?: { request: (type: 'screen') => Promise<{ release: () => Promise<void> }> };
      };
      if (nav.wakeLock && !this.wakeLock) {
        this.wakeLock = await nav.wakeLock.request('screen');
      }
    } catch {
      /* wake lock unsupported or denied — ignore */
    }
  }

  private async releaseWakeLock(): Promise<void> {
    try {
      await this.wakeLock?.release();
    } catch {
      /* ignore */
    }
    this.wakeLock = null;
  }

  /** Orders in a column, oldest first (FIFO for the kitchen/bar). */
  ordersFor(status: string): ServiceOrder[] {
    return this.orders()
      .filter((o) => o.status === status)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  }

  time(iso: string): string {
    return iso.length >= 16 ? iso.substring(11, 16) : iso;
  }

  /** Menu item / order point names may contain embedded HTML formatting. */
  html(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(value);
  }

  // --- status transitions -------------------------------------------------

  ready(o: ServiceOrder): void {
    this.move(o, 'READY');
  }
  back(o: ServiceOrder): void {
    this.move(o, 'ORDERED');
  }
  deliver(o: ServiceOrder): void {
    const prev = this.orders();
    this.orders.set(prev.filter((x) => x.id !== o.id)); // DELIVERED leaves the board
    this.service.updateStatus(o.id, 'DELIVERED').subscribe({
      next: () => this.load(false),
      error: () => {
        this.orders.set(prev);
        this.error.set('Could not update order.');
      },
    });
  }

  private move(o: ServiceOrder, status: Column['status']): void {
    if (o.status === status) return;
    // service only moves between the two board columns; update in place
    const prev = this.orders();
    this.orders.set(prev.map((x) => (x.id === o.id ? { ...x, status } : x)));
    this.service.updateStatus(o.id, status).subscribe({
      next: () => this.load(false),
      error: () => {
        this.orders.set(prev); // revert on failure
        this.error.set('Could not update order.');
      },
    });
  }

  // --- pointer drag (mouse + touch, no long-press) ------------------------

  private candidate:
    | { id: string; x: number; y: number; el: HTMLElement; offX: number; offY: number }
    | null = null;
  private ghost: HTMLElement | null = null;
  private static readonly DRAG_THRESHOLD = 6;

  onPointerDown(o: ServiceOrder, ev: PointerEvent): void {
    if (ev.button && ev.button !== 0) return; // primary button / touch only
    if ((ev.target as HTMLElement).closest('button')) return; // let action buttons work
    const el = ev.currentTarget as HTMLElement;
    const rect = el.getBoundingClientRect();
    this.candidate = {
      id: o.id,
      x: ev.clientX,
      y: ev.clientY,
      el,
      offX: ev.clientX - rect.left,
      offY: ev.clientY - rect.top,
    };
  }

  onPointerMove(ev: PointerEvent): void {
    const c = this.candidate;
    if (!c) return;
    if (!this.dragging()) {
      if (Math.hypot(ev.clientX - c.x, ev.clientY - c.y) < ServicePage.DRAG_THRESHOLD) return;
      this.dragging.set(c.id);
      try {
        c.el.setPointerCapture(ev.pointerId);
      } catch {
        /* ignore */
      }
      this.createGhost(c, ev);
    }
    ev.preventDefault();
    this.moveGhost(c, ev);
    this.dragOver.set(this.columnAt(ev.clientX, ev.clientY));
  }

  onPointerUp(ev: PointerEvent): void {
    const c = this.candidate;
    this.candidate = null;
    if (!c) return;
    if (this.dragging()) {
      const status = this.columnAt(ev.clientX, ev.clientY);
      const o = this.orders().find((x) => x.id === c.id);
      if (o && status && o.status !== status) this.move(o, status);
      this.endDrag(c, ev);
    }
  }

  onPointerCancel(ev: PointerEvent): void {
    const c = this.candidate;
    this.candidate = null;
    if (c && this.dragging()) this.endDrag(c, ev);
  }

  private columnAt(x: number, y: number): Column['status'] | null {
    const el = document.elementFromPoint(x, y) as HTMLElement | null;
    const s = el?.closest('[data-col]')?.getAttribute('data-col');
    return s === 'ORDERED' || s === 'READY' ? s : null;
  }

  private createGhost(c: NonNullable<typeof this.candidate>, ev: PointerEvent): void {
    const rect = c.el.getBoundingClientRect();
    const g = c.el.cloneNode(true) as HTMLElement;
    g.classList.add('drag-ghost');
    g.style.position = 'fixed';
    g.style.width = `${rect.width}px`;
    g.style.left = `${ev.clientX - c.offX}px`;
    g.style.top = `${ev.clientY - c.offY}px`;
    g.style.pointerEvents = 'none';
    g.style.zIndex = '2000';
    document.body.appendChild(g);
    this.ghost = g;
  }

  private moveGhost(c: NonNullable<typeof this.candidate>, ev: PointerEvent): void {
    if (this.ghost) {
      this.ghost.style.left = `${ev.clientX - c.offX}px`;
      this.ghost.style.top = `${ev.clientY - c.offY}px`;
    }
  }

  private endDrag(c: NonNullable<typeof this.candidate>, ev: PointerEvent): void {
    try {
      c.el.releasePointerCapture(ev.pointerId);
    } catch {
      /* ignore */
    }
    this.ghost?.remove();
    this.ghost = null;
    this.dragging.set(null);
    this.dragOver.set(null);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }

  private load(initial: boolean): void {
    if (initial) this.loading.set(true);
    this.service.board().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
        this.error.set(null);
      },
      error: () => {
        this.loading.set(false);
        if (initial) this.error.set('Failed to load orders.');
      },
    });
  }
}
