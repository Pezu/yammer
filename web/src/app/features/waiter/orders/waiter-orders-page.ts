import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActiveOrder, WaiterOrderPointService } from '../tables/waiter-order-point.service';

type Tab = 'ORDERED' | 'READY';

@Component({
  selector: 'app-waiter-orders-page',
  imports: [DecimalPipe],
  template: `
    <header class="page-head">
      <h1>Orders</h1>
      <div class="switch">
        <button type="button" class="sw-btn" [class.active]="tab() === 'ORDERED'" (click)="setTab('ORDERED')">Ordered</button>
        <button type="button" class="sw-btn" [class.active]="tab() === 'READY'" (click)="setTab('READY')">Ready</button>
      </div>
    </header>

    <section class="page-body">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (orders().length === 0) {
        <p class="state">No {{ tab() === 'READY' ? 'ready' : 'ordered' }} orders.</p>
      } @else {
        @for (o of orders(); track o.id) {
          <article class="card">
            <div class="card-head">
              <span class="head-left">
                <span class="op">{{ o.orderPointName }}</span>
                @if (o.createdBy) {
                  <span class="waiter">{{ o.createdBy }}</span>
                }
              </span>
              <span class="time">{{ time(o.createdAt) }}</span>
            </div>
            <ul class="items">
              @for (it of o.items; track it.id) {
                <li>
                  <span class="qty">{{ it.quantity }}×</span>
                  <span class="iname" [innerHTML]="html(it.name)"></span>
                </li>
              }
            </ul>
            <div class="card-foot">
              <span class="total">{{ o.total | number: '1.2-2' }} RON</span>
              @if (o.status === 'READY') {
                <button type="button" class="deliver" [disabled]="busy().has(o.id)" (click)="deliver(o)">
                  Delivered
                </button>
              }
            </div>
          </article>
        }
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 0.85rem 1rem 0.6rem;
      }
      .page-head h1 {
        margin: 0;
        font-size: 0.8rem;
        font-weight: 500;
        text-transform: uppercase;
        letter-spacing: 0.06em;
      }
      .switch {
        display: inline-flex;
        gap: 0.5rem;
      }
      .sw-btn {
        padding: 0.45rem 0.85rem;
        font: inherit;
        font-size: 0.85rem;
        font-weight: 700;
        color: var(--primary);
        background: transparent;
        border: 1px solid #a9c1ee;
        border-radius: 8px;
        cursor: pointer;
        -webkit-tap-highlight-color: transparent;
      }
      .sw-btn:active {
        background: rgba(52, 84, 209, 0.08);
      }
      .sw-btn.active {
        color: #fff;
        background: var(--primary);
        border-color: var(--primary);
      }
      .page-body {
        padding: 0.5rem 0.75rem 1.5rem;
      }
      .state {
        margin: 2rem 0;
        text-align: center;
        color: var(--muted);
        font-size: 0.95rem;
      }
      .state.err {
        color: var(--danger);
      }
      .card {
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 10px;
        padding: 0.7rem 0.8rem;
        margin-bottom: 0.6rem;
      }
      .card-head {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.5rem;
      }
      .head-left {
        display: inline-flex;
        align-items: baseline;
        gap: 0.45rem;
        min-width: 0;
      }
      .op {
        font-size: 0.98rem;
        font-weight: 800;
      }
      .waiter {
        font-size: 0.74rem;
        color: var(--muted);
      }
      .time {
        font-size: 0.78rem;
        color: var(--muted);
        font-variant-numeric: tabular-nums;
      }
      .items {
        list-style: none;
        margin: 0.5rem 0 0;
        padding: 0;
      }
      .items li {
        display: flex;
        gap: 0.45rem;
        font-size: 0.85rem;
        line-height: 1.8;
      }
      .qty {
        font-weight: 700;
        color: var(--text);
        min-width: 1.7rem;
      }
      .card-foot {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-top: 0.6rem;
        padding-top: 0.55rem;
        border-top: 1px solid var(--border);
      }
      .total {
        font-size: 0.86rem;
        font-weight: 800;
        font-variant-numeric: tabular-nums;
      }
      .deliver {
        padding: 0.4rem 0.9rem;
        font-size: 0.8rem;
        font-weight: 700;
        color: #0f9d63;
        background: #fff;
        border: 1px solid #10b981;
        border-radius: 8px;
        cursor: pointer;
      }
      .deliver:disabled {
        opacity: 0.5;
      }
    `,
  ],
})
export class WaiterOrdersPage {
  private readonly service = inject(WaiterOrderPointService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly orders = signal<ActiveOrder[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal<Set<string>>(new Set());
  readonly tab = signal<Tab>('ORDERED');

  constructor() {
    this.load();
  }

  /** Switch tab → fetch that status from the backend. */
  setTab(tab: Tab): void {
    if (this.tab() === tab) return;
    this.tab.set(tab);
    this.load();
  }

  time(iso: string): string {
    return iso.length >= 16 ? iso.substring(11, 16) : iso;
  }

  html(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(value);
  }

  deliver(o: ActiveOrder): void {
    if (o.status !== 'READY' || this.busy().has(o.id)) return;
    this.busy.update((s) => new Set(s).add(o.id));
    const prev = this.orders();
    this.orders.set(prev.filter((x) => x.id !== o.id)); // optimistic
    this.service.setOrderStatus(o.id, 'DELIVERED').subscribe({
      next: () => this.clearBusy(o.id),
      error: () => {
        this.orders.set(prev);
        this.clearBusy(o.id);
        this.error.set('Could not update order.');
      },
    });
  }

  private clearBusy(id: string): void {
    this.busy.update((s) => {
      const next = new Set(s);
      next.delete(id);
      return next;
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.activeOrders(this.tab()).subscribe({
      next: (orders) => {
        this.orders.set([...orders].sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to load orders.');
      },
    });
  }
}
