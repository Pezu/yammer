import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import {
  CustomerOrderPoint,
  CustomerOrderPointService,
  MenuNode,
} from './customer-order-point.service';
import { TransparentImageDirective } from '../../shared/transparent-image.directive';

/**
 * Landing/ordering page a customer reaches by scanning an order point's QR code
 * (`/customer/order-point/:opId`). Shows the order point's default menu and lets the customer
 * build a cart and place a (pay-later) order. The event is resolved from the order point.
 */
@Component({
  selector: 'app-customer-order-point-page',
  imports: [DecimalPipe, NgTemplateOutlet, TransparentImageDirective],
  template: `
    <header class="topbar">
      <div class="brand">
        <img class="logo" [src]="logoSrc()" alt="logo" (error)="logoFailed.set(true)" />
      </div>
      <button type="button" class="hamburger" (click)="toggleMenu()" aria-label="Menu" aria-haspopup="true">
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
      </button>
    </header>

    @if (menuOpen()) {
      <div class="drawer-backdrop" (click)="closeMenu()"></div>
      <nav class="drawer">
        <button type="button" class="drawer-item active" (click)="selectOrder()">Order</button>
      </nav>
    }

    <main class="cust">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (op(); as o) {
        @if (placed()) {
          <div class="placed" role="status">Your order has been sent. Thank you!</div>
        }

        @if (o.menu.length > 0) {
          @if (topCategories().length > 1) {
            <nav class="cat-nav">
              @for (cat of topCategories(); track cat.id) {
                <button type="button" class="cat-chip" (click)="scrollTo(cat.id)" [innerHTML]="cat.name"></button>
              }
            </nav>
          }
          <div class="menu-tree">
            @for (node of o.menu; track node.id) {
              <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: node, level: 0 }"></ng-container>
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

    <!-- Recursive node: an orderable product renders as a row; a category renders a header + its children. -->
    <ng-template #nodeTpl let-node let-level="level">
      @if (node.orderable) {
        <div class="item-card">
          @if (node.imageObject && !transparentImages().has(node.imageObject)) {
            <img
              class="item-img"
              appTransparentCheck
              (transparent)="markTransparent(node.imageObject)"
              [src]="imageUrl(node.imageObject)"
              alt=""
            />
          } @else {
            <div class="item-img placeholder"></div>
          }
          <div class="item-body">
            <span class="item-name" [innerHTML]="node.name"></span>
          </div>
          <div class="item-side">
            @if (node.price != null) {
              <span class="item-price">{{ node.price | number: '1.2-2' }} RON</span>
            }
            <div class="qty">
              @if (qty(node.id) > 0) {
                <button type="button" class="qty-btn" aria-label="Remove one" (click)="dec(node.id)">−</button>
                <span class="qty-val">{{ qty(node.id) }}</span>
              }
              <button type="button" class="qty-btn" aria-label="Add one" (click)="inc(node)">+</button>
            </div>
          </div>
        </div>
      } @else {
        <section class="menu-cat" [id]="'cat-' + node.id">
          <div class="cat-head" [class.sub]="level > 0">
            <span class="cat-name" [innerHTML]="node.name"></span>
          </div>
          <div class="cat-children">
            @for (child of node.children; track child.id) {
              <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: child, level: level + 1 }"></ng-container>
            }
          </div>
        </section>
      }
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: #fff;
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 10;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      height: 56px;
      padding: 0 1rem;
      background: #fff;
      border-bottom: 1px solid var(--border);
    }
    .brand {
      display: flex;
      align-items: center;
      min-width: 0;
    }
    .logo {
      width: 44px;
      height: 44px;
      object-fit: contain;
      padding: 4px;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(18, 27, 46, 0.08);
    }
    .brand-name {
      font-weight: 800;
      color: var(--text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .hamburger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      color: var(--text);
      background: none;
      border: none;
      cursor: pointer;
    }
    .drawer-backdrop {
      position: fixed;
      inset: 0;
      z-index: 20;
      background: rgba(18, 27, 46, 0.35);
    }
    .drawer {
      position: fixed;
      top: 0;
      right: 0;
      bottom: 0;
      z-index: 21;
      width: 240px;
      max-width: 80vw;
      padding: 4rem 0.75rem 1rem;
      background: #fff;
      box-shadow: -0.5rem 0 1.5rem rgba(18, 27, 46, 0.15);
    }
    .drawer-item {
      display: block;
      width: 100%;
      padding: 0.85rem 1rem;
      font: inherit;
      font-size: 1rem;
      font-weight: 600;
      text-align: left;
      color: var(--text);
      background: none;
      border: none;
      border-radius: 8px;
      cursor: pointer;
    }
    .drawer-item:hover,
    .drawer-item.active {
      background: var(--page-bg);
      color: var(--primary);
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
    /* servio-style: horizontal category quick-nav (jump to section) */
    .cat-nav {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      margin: 0.25rem 0 1rem;
      padding-bottom: 4px;
      scrollbar-width: none;
    }
    .cat-nav::-webkit-scrollbar {
      display: none;
    }
    .cat-chip {
      flex-shrink: 0;
      padding: 7px 14px;
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
      white-space: nowrap;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 999px;
      cursor: pointer;
    }
    /* servio-style: single scrolling list, products one per row */
    .menu-tree {
      display: flex;
      flex-direction: column;
      gap: 0;
      text-align: left;
    }
    .menu-cat {
      scroll-margin-top: 72px;
    }
    .cat-children {
      display: flex;
      flex-direction: column;
      gap: 0;
    }
    .cat-head {
      padding: 28px 0 8px;
    }
    .cat-head.sub {
      padding-top: 12px;
    }
    .cat-name {
      display: block;
      font-size: 14px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text);
    }
    .cat-head.sub .cat-name {
      font-size: 12px;
      font-weight: 600;
      text-transform: none;
      letter-spacing: 0;
      color: var(--muted);
    }
    .item-card {
      display: flex;
      align-items: stretch;
      gap: 12px;
      min-height: 92px;
      padding: 14px 2px;
      background: #fff;
      border-bottom: 1px solid var(--border);
    }
    .item-img {
      flex-shrink: 0;
      align-self: center;
      width: 64px;
      height: 64px;
      object-fit: cover;
      border-radius: 8px;
    }
    .item-img.placeholder {
      background: var(--page-bg);
      border: 1px dashed var(--border);
    }
    .item-body {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .item-name {
      font-size: 14px;
      font-weight: 600;
      line-height: 1.25;
      color: var(--text);
    }
    .item-price {
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
      font-variant-numeric: tabular-nums;
    }
    .item-side {
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 8px;
    }
    .qty {
      margin-top: auto;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .qty-btn {
      width: 24px;
      height: 24px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      font-weight: 700;
      line-height: 1;
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 50%;
      cursor: pointer;
    }
    .qty-val {
      min-width: 18px;
      font-size: 14px;
      font-weight: 700;
      text-align: center;
      color: var(--text);
      font-variant-numeric: tabular-nums;
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
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--primary);
      border-radius: 8px;
      cursor: pointer;
    }
    .order-btn:disabled {
      opacity: 0.6;
      cursor: default;
    }
  `,
})
export class CustomerOrderPointPage implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CustomerOrderPointService);
  private readonly opId = this.route.snapshot.paramMap.get('opId') ?? '';

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly op = signal<CustomerOrderPoint | null>(null);

  // app bar / drawer
  readonly menuOpen = signal(false);
  readonly logoFailed = signal(false);

  // cart: product id -> quantity
  readonly cart = signal<Record<string, number>>({});
  readonly placing = signal(false);
  readonly placed = signal(false);

  // top-level categories, surfaced as quick-nav chips that scroll to each section
  readonly topCategories = computed<MenuNode[]>(() =>
    (this.op()?.menu ?? []).filter((n) => !n.orderable),
  );

  // image objects detected to have a transparent background — hidden, placeholder shown instead
  readonly transparentImages = signal<Set<string>>(new Set());
  markTransparent(object: string): void {
    this.transparentImages.update((s) => new Set(s).add(object));
  }

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
    // The customer page is full-white (no admin gray peeking through on mobile overscroll).
    document.body.style.background = '#fff';
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

  ngOnDestroy(): void {
    document.body.style.background = '';
  }

  imageUrl(object: string): string {
    return this.service.imageUrl(object);
  }

  /** Smooth-scroll the menu to a top-level category section. */
  scrollTo(categoryId: string): void {
    document
      .getElementById('cat-' + categoryId)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Brand logo: the client's logo, falling back to the app placeholder when absent/unloadable. */
  logoSrc(): string {
    const clientId = this.op()?.clientId;
    if (!clientId || this.logoFailed()) return 'assets/images/logo-abbr.png';
    return this.service.clientLogoUrl(clientId);
  }

  toggleMenu(): void {
    this.menuOpen.update((o) => !o);
  }
  closeMenu(): void {
    this.menuOpen.set(false);
  }
  /** Only menu item for now — shows the menu (the default content). */
  selectOrder(): void {
    this.closeMenu();
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
    const returnUrl = `${window.location.origin}/customer/order-point/${this.opId}/payment-return`;
    this.placing.set(true);
    this.error.set(null);
    this.service.placeOrder(this.opId, items, returnUrl).subscribe({
      next: (result) => {
        if (result.paymentUrl) {
          // Pay-now: hand off to the gateway. Don't clear placing — we're navigating away.
          window.location.href = result.paymentUrl;
          return;
        }
        // Pay-later: order created.
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
