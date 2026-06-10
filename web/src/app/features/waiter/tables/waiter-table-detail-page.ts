import { Component, computed, HostListener, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import {
  Payment,
  PaymentMethod,
  PlacedOrder,
  WaiterOrderPointService,
} from './waiter-order-point.service';
import { ToastService } from '../../../core/toast.service';

interface ProductLine {
  menuItemId: string;
  name: string;
  price: number | null;
  qty: number; // unpaid quantity for this product across the table order
}

type TipMode = 'none' | 'p10' | 'p12' | 'p15' | 'customPct' | 'customAmt';

@Component({
  selector: 'app-waiter-table-detail-page',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './waiter-table-detail-page.html',
  styleUrl: './waiter-table-detail-page.scss',
})
export class WaiterTableDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(WaiterOrderPointService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toast = inject(ToastService);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  readonly name = signal<string>(history.state?.name ?? '');
  /** Whether this order point is a protocol (comp/house) table. */
  readonly protocol = signal(false);

  readonly orders = signal<PlacedOrder[]>([]);
  readonly payments = signal<Payment[]>([]);
  readonly loadingOrders = signal(true);
  readonly ordersError = signal<string | null>(null);

  /** Unpaid lines grouped by product — the current bill. */
  readonly unpaidByProduct = computed<ProductLine[]>(() => {
    const map = new Map<string, ProductLine>();
    for (const order of this.orders()) {
      for (const it of order.items) {
        if (it.paid || !it.menuItemId) {
          continue;
        }
        const existing = map.get(it.menuItemId);
        if (existing) {
          existing.qty += it.quantity;
        } else {
          map.set(it.menuItemId, {
            menuItemId: it.menuItemId,
            name: it.name,
            price: it.price,
            qty: it.quantity,
          });
        }
      }
    }
    return [...map.values()];
  });

  /** Paid lines grouped by product. */
  readonly paidByProduct = computed<ProductLine[]>(() => {
    const map = new Map<string, ProductLine>();
    for (const order of this.orders()) {
      for (const it of order.items) {
        if (!it.paid || !it.menuItemId) {
          continue;
        }
        const existing = map.get(it.menuItemId);
        if (existing) {
          existing.qty += it.quantity;
        } else {
          map.set(it.menuItemId, {
            menuItemId: it.menuItemId,
            name: it.name,
            price: it.price,
            qty: it.quantity,
          });
        }
      }
    }
    return [...map.values()];
  });
  // which list to show: unpaid (default) or paid
  readonly view = signal<'unpaid' | 'paid'>('unpaid');
  readonly viewComboOpen = signal(false);
  toggleViewCombo(): void {
    this.viewComboOpen.update((o) => !o);
  }
  closeViewCombo(): void {
    this.viewComboOpen.set(false);
  }
  selectView(v: 'unpaid' | 'paid'): void {
    this.view.set(v);
    this.viewComboOpen.set(false);
  }

  readonly orderedTotal = computed(() =>
    this.orders().reduce(
      (s, o) => s + o.items.reduce((a, it) => a + (it.price ?? 0) * it.quantity, 0),
      0,
    ),
  );
  readonly remaining = computed(() =>
    round2(this.unpaidByProduct().reduce((s, p) => s + (p.price ?? 0) * p.qty, 0)),
  );
  readonly paidTotal = computed(() => round2(this.orderedTotal() - this.remaining()));
  readonly fullyPaid = computed(() => this.orderedTotal() > 0 && this.remaining() <= 0);

  // --- pay menu (groups Full / Partial) ---
  readonly payMenuOpen = signal(false);
  togglePayMenu(): void {
    this.payMenuOpen.update((o) => !o);
  }
  closePayMenu(): void {
    this.payMenuOpen.set(false);
  }

  // --- proforma (non-fiscal bill) ---
  readonly printingProforma = signal(false);
  printProforma(): void {
    if (this.printingProforma() || this.remaining() <= 0) {
      return;
    }
    this.printingProforma.set(true);
    this.service.printProforma(this.id).subscribe({
      next: () => {
        this.printingProforma.set(false);
        this.toast.show('Proforma sent to printer');
      },
      error: () => {
        this.printingProforma.set(false);
        this.toast.show('Failed to print proforma');
      },
    });
  }

  // --- payment modal ---
  readonly payOpen = signal(false);
  readonly partial = signal(false);
  readonly submitting = signal(false);
  readonly payError = signal<string | null>(null);

  /** product id -> quantity selected to pay (partial mode). */
  readonly paying = signal<Record<string, number>>({});
  readonly dragged = signal<{ id: string; source: 'bill' | 'paying' } | null>(null);

  readonly billList = computed(() =>
    this.unpaidByProduct()
      .map((p) => ({ ...p, rem: p.qty - (this.paying()[p.menuItemId] ?? 0) }))
      .filter((p) => p.rem > 0),
  );
  readonly payingList = computed(() =>
    this.unpaidByProduct()
      .map((p) => ({ ...p, pay: this.paying()[p.menuItemId] ?? 0 }))
      .filter((p) => p.pay > 0),
  );
  readonly payingTotal = computed(() =>
    round2(this.payingList().reduce((s, p) => s + (p.price ?? 0) * p.pay, 0)),
  );
  readonly amount = computed(() => (this.partial() ? this.payingTotal() : this.remaining()));

  readonly tipMode = signal<TipMode>('none');
  readonly tipCustomPercent = signal<number | null>(null);
  readonly tipCustomAmount = signal<number | null>(null);
  readonly computedTip = computed(() => {
    const base = this.amount() || 0;
    let tip = 0;
    switch (this.tipMode()) {
      case 'p10':
        tip = base * 0.1;
        break;
      case 'p12':
        tip = base * 0.12;
        break;
      case 'p15':
        tip = base * 0.15;
        break;
      case 'customPct': {
        const p = this.tipCustomPercent();
        if (p != null && !isNaN(p)) {
          tip = base * (p / 100);
        }
        break;
      }
      case 'customAmt': {
        const a = this.tipCustomAmount();
        if (a != null && !isNaN(a)) {
          tip = a;
        }
        break;
      }
    }
    return Math.max(0, round2(tip));
  });
  readonly totalToPay = computed(() => round2(this.amount() + this.computedTip()));

  constructor() {
    // always fetch to learn the protocol flag (name still comes instantly via state)
    this.service.mine().subscribe({
      next: (rows) => {
        const op = rows.find((r) => r.id === this.id);
        if (op) {
          if (!this.name()) {
            this.name.set(op.name);
          }
          this.protocol.set(op.protocol);
        }
      },
    });
    this.reload();
  }

  private reload(): void {
    this.loadingOrders.set(true);
    this.service.orders(this.id).subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loadingOrders.set(false);
      },
      error: () => {
        this.ordersError.set('Could not load orders.');
        this.loadingOrders.set(false);
      },
    });
    this.service.payments(this.id).subscribe({ next: (p) => this.payments.set(p), error: () => {} });
  }

  html(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(value);
  }

  // --- payment modal ---
  openPayment(partial: boolean): void {
    this.payMenuOpen.set(false);
    if (this.remaining() <= 0) {
      return;
    }
    this.partial.set(partial);
    this.paying.set({});
    this.tipMode.set('none');
    this.tipCustomPercent.set(null);
    this.tipCustomAmount.set(null);
    this.payError.set(null);
    this.payOpen.set(true);
  }
  closePayment(): void {
    this.payOpen.set(false);
  }
  setTip(mode: TipMode): void {
    this.tipMode.set(mode);
  }

  // --- drag/tap move between Bill and Paying ---
  moveToPaying(id: string): void {
    const product = this.unpaidByProduct().find((p) => p.menuItemId === id);
    if (!product) {
      return;
    }
    const cur = this.paying()[id] ?? 0;
    if (cur >= product.qty) {
      return;
    }
    this.paying.update((m) => ({ ...m, [id]: cur + 1 }));
  }
  moveToBill(id: string): void {
    const cur = this.paying()[id] ?? 0;
    if (cur <= 0) {
      return;
    }
    this.paying.update((m) => {
      const next = { ...m };
      if (cur - 1 <= 0) {
        delete next[id];
      } else {
        next[id] = cur - 1;
      }
      return next;
    });
  }
  // mouse: native HTML5 drag-and-drop
  onDragStart(id: string, source: 'bill' | 'paying'): void {
    this.dragged.set({ id, source });
  }
  dropToPaying(): void {
    const d = this.dragged();
    if (d?.source === 'bill') {
      this.moveToPaying(d.id);
    }
    this.dragged.set(null);
  }
  dropToBill(): void {
    const d = this.dragged();
    if (d?.source === 'paying') {
      this.moveToBill(d.id);
    }
    this.dragged.set(null);
  }

  // tap (mouse + touch): move one unit; suppressed right after a real touch-drag
  private justDragged = false;
  onChipClick(id: string, source: 'bill' | 'paying'): void {
    if (this.justDragged) {
      this.justDragged = false;
      return;
    }
    if (source === 'bill') {
      this.moveToPaying(id);
    } else {
      this.moveToBill(id);
    }
  }

  // touch/pen: a pointer-drag shim with a floating ghost (HTML5 DnD doesn't fire on touch)
  private touchDrag: {
    id: string;
    source: 'bill' | 'paying';
    startX: number;
    startY: number;
    moved: boolean;
    ghost?: HTMLElement;
  } | null = null;

  onChipPointerDown(event: PointerEvent, id: string, source: 'bill' | 'paying'): void {
    if (event.pointerType === 'mouse') {
      return; // mouse uses native DnD + click
    }
    this.touchDrag = { id, source, startX: event.clientX, startY: event.clientY, moved: false };
  }

  @HostListener('document:pointermove', ['$event'])
  onDocPointerMove(event: PointerEvent): void {
    const d = this.touchDrag;
    if (!d) {
      return;
    }
    if (!d.moved && Math.hypot(event.clientX - d.startX, event.clientY - d.startY) < 8) {
      return;
    }
    if (!d.moved) {
      d.moved = true;
      d.ghost = this.makeGhost(d.id);
    }
    event.preventDefault();
    if (d.ghost) {
      d.ghost.style.left = `${event.clientX}px`;
      d.ghost.style.top = `${event.clientY}px`;
    }
  }

  @HostListener('document:pointerup', ['$event'])
  onDocPointerUp(event: PointerEvent): void {
    const d = this.touchDrag;
    if (!d) {
      return;
    }
    this.touchDrag = null;
    d.ghost?.remove();
    if (!d.moved) {
      return; // it was a tap — let the click handler move it
    }
    this.justDragged = true; // suppress the synthetic click that follows
    setTimeout(() => (this.justDragged = false), 400);
    const zone = (document.elementFromPoint(event.clientX, event.clientY) as HTMLElement | null)
      ?.closest('[data-zone]')
      ?.getAttribute('data-zone');
    if (zone === 'paying' && d.source === 'bill') {
      this.moveToPaying(d.id);
    } else if (zone === 'bill' && d.source === 'paying') {
      this.moveToBill(d.id);
    }
  }

  private makeGhost(id: string): HTMLElement {
    const product = this.unpaidByProduct().find((p) => p.menuItemId === id);
    const el = document.createElement('div');
    el.textContent = this.plainText(product?.name ?? '');
    el.style.cssText =
      'position:fixed;transform:translate(-50%,-50%);z-index:9999;pointer-events:none;' +
      'padding:0.4rem 0.75rem;border-radius:999px;background:#3454d1;color:#fff;' +
      "font:700 0.85rem system-ui,sans-serif;box-shadow:0 0.5rem 1rem rgba(18,27,46,0.3);";
    document.body.appendChild(el);
    return el;
  }
  private plainText(htmlValue: string): string {
    const tmp = document.createElement('div');
    tmp.innerHTML = htmlValue;
    return tmp.textContent ?? '';
  }

  pay(method: PaymentMethod): void {
    if (this.submitting()) {
      return;
    }
    // protocol settles the bill at no charge, so a zero amount is fine
    if (!this.protocol() && this.amount() <= 0) {
      return;
    }
    const mode = this.partial() ? 'PARTIAL' : 'FULL';
    const items = this.partial()
      ? this.payingList().map((p) => ({ menuItemId: p.menuItemId, quantity: p.pay }))
      : [];
    this.submitting.set(true);
    this.payError.set(null);
    this.service.payLines(this.id, mode, method, this.computedTip(), items).subscribe({
      next: () => {
        this.submitting.set(false);
        this.payOpen.set(false);
        this.toast.show('Payment recorded');
        this.reload();
      },
      error: () => {
        this.submitting.set(false);
        this.payError.set('Could not record the payment.');
      },
    });
  }
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
