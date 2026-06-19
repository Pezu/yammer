import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import {
  CustomerOrderPoint,
  CustomerOrderPointService,
  MenuNode,
} from './customer-order-point.service';

/**
 * Landing/ordering page a customer reaches by scanning an order point's QR code
 * (`/customer/order-point/:opId`). Shows the order point's default menu and lets the customer
 * build a cart and place a (pay-later) order. The event is resolved from the order point.
 */
@Component({
  selector: 'app-customer-order-point-page',
  imports: [DecimalPipe, NgTemplateOutlet],
  template: `
    <main class="cust">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (op(); as o) {
        <header class="cust-head">
          @if (o.eventName) {
            <p class="event">{{ o.eventName }}</p>
          }
          <h1 class="table">{{ o.name }}</h1>
        </header>

        @if (placed()) {
          <div class="placed" role="status">Your order has been sent. Thank you!</div>
        }

        @if (o.menu.length > 0) {
          <div class="menu">
            <ng-template #nodeTpl let-n>
              @if (n.orderable) {
                <div class="product">
                  <div class="pinfo">
                    <span class="pname" [innerHTML]="n.name"></span>
                    @if (n.price != null) {
                      <span class="pprice">{{ n.price | number: '1.2-2' }}</span>
                    }
                  </div>
                  <div class="stepper">
                    <button type="button" (click)="dec(n.id)" [disabled]="qty(n.id) <= 0" aria-label="Remove one">−</button>
                    <span class="qv">{{ qty(n.id) }}</span>
                    <button type="button" (click)="inc(n)" aria-label="Add one">+</button>
                  </div>
                </div>
              } @else {
                <section class="category">
                  <h2 class="cname" [innerHTML]="n.name"></h2>
                  @for (c of n.children; track c.id) {
                    <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: c }" />
                  }
                </section>
              }
            </ng-template>
            @for (n of o.menu; track n.id) {
              <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: n }" />
            }
          </div>
        } @else {
          <p class="soon">No menu available for this table yet.</p>
        }
      }

      @if (cartCount() > 0) {
        <footer class="cart-bar">
          <div class="cart-info">
            <span class="cart-count">{{ cartCount() }} item{{ cartCount() === 1 ? '' : 's' }}</span>
            <span class="cart-total">{{ cartTotal() | number: '1.2-2' }}</span>
          </div>
          <button type="button" class="order-btn" [disabled]="placing()" (click)="placeOrder()">
            {{ placing() ? 'Sending…' : 'Place order' }}
          </button>
        </footer>
      }
    </main>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: #fff;
    }
    .cust {
      max-width: 30rem;
      margin: 0 auto;
      padding: 2rem 1.25rem 6rem;
      text-align: center;
    }
    .state {
      margin: 3rem 0;
      color: var(--muted);
    }
    .state.err {
      color: var(--danger);
    }
    .cust-head {
      margin: 1.5rem 0 1rem;
    }
    .event {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted);
    }
    .table {
      margin: 0.25rem 0 0;
      font-size: 2rem;
      font-weight: 800;
      color: var(--text);
    }
    .placed {
      margin: 0.75rem 0;
      padding: 0.6rem 0.85rem;
      font-size: 0.9rem;
      color: #1f7a3d;
      background: rgba(40, 167, 69, 0.1);
      border: 1px solid rgba(40, 167, 69, 0.25);
      border-radius: 8px;
    }
    .soon {
      color: var(--muted);
      font-size: 0.9rem;
      text-align: center;
    }
    .menu {
      margin-top: 1rem;
      text-align: left;
    }
    .category {
      margin: 1.25rem 0;
    }
    .cname {
      margin: 0 0 0.5rem;
      font-size: 1rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--muted);
      border-bottom: 1px solid var(--border);
      padding-bottom: 0.35rem;
    }
    .category .category {
      margin-left: 0.75rem;
    }
    .product {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.6rem 0;
      border-bottom: 1px solid var(--border);
    }
    .pinfo {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.15rem;
      min-width: 0;
    }
    .pname {
      color: var(--text);
    }
    .pprice {
      font-variant-numeric: tabular-nums;
      font-size: 0.85rem;
      color: var(--muted);
    }
    .stepper {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      flex: none;
    }
    .stepper button {
      width: 32px;
      height: 32px;
      font-size: 1.2rem;
      line-height: 1;
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 50%;
      cursor: pointer;
    }
    .stepper button:disabled {
      color: var(--muted);
      opacity: 0.4;
      cursor: default;
    }
    .qv {
      min-width: 1.25rem;
      text-align: center;
      font-variant-numeric: tabular-nums;
      font-weight: 600;
    }
    .cart-bar {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      max-width: 30rem;
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.75rem 1.25rem;
      background: #fff;
      border-top: 1px solid var(--border);
      box-shadow: 0 -0.5rem 1rem rgba(18, 27, 46, 0.08);
    }
    .cart-info {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
    }
    .cart-count {
      font-size: 0.78rem;
      color: var(--muted);
    }
    .cart-total {
      font-size: 1.1rem;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
      color: var(--text);
    }
    .order-btn {
      padding: 0.7rem 1.5rem;
      font: inherit;
      font-weight: 700;
      color: #fff;
      background: var(--primary);
      border: none;
      border-radius: 8px;
      cursor: pointer;
    }
    .order-btn:disabled {
      opacity: 0.6;
      cursor: default;
    }
  `,
})
export class CustomerOrderPointPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CustomerOrderPointService);
  private readonly opId = this.route.snapshot.paramMap.get('opId') ?? '';

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly op = signal<CustomerOrderPoint | null>(null);

  // cart: product id -> quantity
  readonly cart = signal<Record<string, number>>({});
  readonly placing = signal(false);
  readonly placed = signal(false);

  /** Flat list of orderable products in the menu, for cart totals / price lookup. */
  private readonly products = computed(() => {
    const out: MenuNode[] = [];
    const walk = (nodes: MenuNode[]) => {
      for (const n of nodes) {
        if (n.orderable) out.push(n);
        if (n.children?.length) walk(n.children);
      }
    };
    walk(this.op()?.menu ?? []);
    return out;
  });
  private readonly priceById = computed(() => {
    const m = new Map<string, number>();
    for (const p of this.products()) m.set(p.id, p.price ?? 0);
    return m;
  });

  readonly cartCount = computed(() =>
    Object.values(this.cart()).reduce((s, q) => s + q, 0),
  );
  readonly cartTotal = computed(() => {
    const price = this.priceById();
    return Object.entries(this.cart()).reduce(
      (s, [id, q]) => s + (price.get(id) ?? 0) * q,
      0,
    );
  });

  constructor() {
    if (!this.opId) {
      this.error.set('Invalid link.');
      this.loading.set(false);
      return;
    }
    this.service.getOrderPoint(this.opId).subscribe({
      next: (op) => {
        this.op.set(op);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('This table could not be found.');
        this.loading.set(false);
      },
    });
  }

  qty(id: string): number {
    return this.cart()[id] ?? 0;
  }
  inc(n: MenuNode): void {
    this.placed.set(false);
    this.cart.update((c) => ({ ...c, [n.id]: (c[n.id] ?? 0) + 1 }));
  }
  dec(id: string): void {
    this.cart.update((c) => {
      const next = { ...c };
      const q = (next[id] ?? 0) - 1;
      if (q <= 0) delete next[id];
      else next[id] = q;
      return next;
    });
  }

  placeOrder(): void {
    if (this.placing() || this.cartCount() === 0) return;
    const items = Object.entries(this.cart()).map(([menuItemId, quantity]) => ({ menuItemId, quantity }));
    this.placing.set(true);
    this.error.set(null);
    this.service.placeOrder(this.opId, items).subscribe({
      next: () => {
        this.cart.set({});
        this.placing.set(false);
        this.placed.set(true);
      },
      error: () => {
        this.placing.set(false);
        this.error.set('Could not place your order. Please try again.');
      },
    });
  }
}
