import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { MenuNode, OrderPointMenu, WaiterOrderPointService } from './waiter-order-point.service';
import { ToastService } from '../../../core/toast.service';

interface CartLine {
  menuItemId: string;
  name: string;
  price: number;
  quantity: number;
}

@Component({
  selector: 'app-waiter-order-page',
  imports: [DecimalPipe],
  templateUrl: './waiter-order-page.html',
  styleUrl: './waiter-order-page.scss',
})
export class WaiterOrderPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly service = inject(WaiterOrderPointService);
  private readonly toast = inject(ToastService);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly stateName = (history.state?.name as string) ?? '';

  readonly menu = signal<OrderPointMenu | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /**
   * Both the drill-down path (?c=catId1,catId2) and the cart view (?s=1) live in
   * the URL, so each is a real history entry — hardware/router back walks one step
   * up the menu (and closes the cart) instead of leaving the page.
   */
  private readonly query = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  readonly summaryOpen = computed(() => this.query().get('s') === '1');
  private readonly pathIds = computed(() => {
    const c = this.query().get('c');
    return c ? c.split(',') : [];
  });

  readonly stack = computed<MenuNode[]>(() => {
    const result: MenuNode[] = [];
    let level = this.menu()?.items ?? [];
    for (const id of this.pathIds()) {
      const node = level.find((n) => n.id === id && !n.orderable);
      if (!node) {
        break;
      }
      result.push(node);
      level = node.children ?? [];
    }
    return result;
  });

  readonly currentItems = computed<MenuNode[]>(() => {
    const s = this.stack();
    const items = s.length === 0 ? this.menu()?.items ?? [] : s[s.length - 1].children ?? [];
    // categories (orderable=false) before products among siblings
    return [...items].sort((a, b) => Number(a.orderable) - Number(b.orderable));
  });

  // Bound via [innerHTML], which auto-sanitizes the plain string (no bypass → no stored XSS).
  readonly titleHtml = computed<string>(() => {
    if (this.summaryOpen()) {
      return 'Cart';
    }
    const s = this.stack();
    return s.length
      ? s[s.length - 1].name
      : this.menu()?.orderPointName || this.stateName || 'Menu';
  });

  // --- cart ---
  readonly cart = signal<CartLine[]>([]);
  readonly placing = signal(false);
  readonly placeError = signal<string | null>(null);
  readonly totalItems = computed(() => this.cart().reduce((s, l) => s + l.quantity, 0));
  readonly total = computed(() => this.cart().reduce((s, l) => s + l.price * l.quantity, 0));

  constructor() {
    this.service.menu(this.id).subscribe({
      next: (m) => {
        this.menu.set(m);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the menu.');
        this.loading.set(false);
      },
    });
  }

  isCategory(n: MenuNode): boolean {
    return !n.orderable;
  }
  qty(itemId: string): number {
    return this.cart().find((l) => l.menuItemId === itemId)?.quantity ?? 0;
  }

  tap(n: MenuNode): void {
    if (this.isCategory(n)) {
      const path = [...this.pathIds(), n.id];
      this.router.navigate([], { relativeTo: this.route, queryParams: { c: path.join(',') } });
    } else if (n.orderable) {
      this.increase(n);
    }
  }

  increase(n: MenuNode): void {
    this.cart.update((list) => {
      const existing = list.find((l) => l.menuItemId === n.id);
      if (existing) {
        return list.map((l) => (l.menuItemId === n.id ? { ...l, quantity: l.quantity + 1 } : l));
      }
      return [...list, { menuItemId: n.id, name: n.name, price: n.price ?? 0, quantity: 1 }];
    });
  }

  decrease(id: string, event?: Event): void {
    event?.stopPropagation();
    this.cart.update((list) =>
      list.flatMap((l) => {
        if (l.menuItemId !== id) {
          return [l];
        }
        return l.quantity > 1 ? [{ ...l, quantity: l.quantity - 1 }] : [];
      }),
    );
  }

  /** Open the cart summary (a history entry, so back closes it). */
  openSummary(): void {
    if (!this.cart().length) {
      return;
    }
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { s: '1' },
      queryParamsHandling: 'merge',
    });
  }

  placeOrder(): void {
    const lines = this.cart();
    if (!lines.length || this.placing()) {
      return;
    }
    this.placing.set(true);
    this.placeError.set(null);
    this.service
      .placeOrder(
        this.id,
        lines.map((l) => ({
          menuItemId: l.menuItemId,
          name: l.name,
          price: l.price,
          quantity: l.quantity,
        })),
      )
      .subscribe({
        next: () => {
          this.cart.set([]);
          this.toast.show('Order placed');
          // rewrite the cart entry to the Tables URL (silently) so a back from the
          // orders page exits the table instead of returning to the cart/menu
          this.location.replaceState('/waiter/tables');
          this.router.navigate(['/waiter/tables', this.id]);
        },
        error: () => {
          this.placing.set(false);
          this.placeError.set('Could not place the order. Please try again.');
        },
      });
  }

  /** Semantic back: cart → menu, category → parent category, menu root → the table page. */
  back(): void {
    if (this.summaryOpen()) {
      this.toMenu(this.pathIds());
    } else if (this.pathIds().length) {
      this.toMenu(this.pathIds().slice(0, -1));
    } else {
      this.router.navigate(['/waiter/tables', this.id]);
    }
  }

  private toMenu(path: string[]): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: path.length ? { c: path.join(',') } : {},
    });
  }
}
