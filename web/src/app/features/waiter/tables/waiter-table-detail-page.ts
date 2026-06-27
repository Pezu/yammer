import { Component, computed, HostListener, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  BillLine,
  PaymentMethod,
  WaiterOrderPointService,
} from './waiter-order-point.service';
import { ToastService } from '../../../core/toast.service';
import { PayModal } from '../../../shared/pay-modal/pay-modal';

interface ProductLine {
  menuItemId: string;
  name: string;
  price: number | null;
  qty: number; // quantity for this product (paid or unpaid, depending on the view)
}

type TipMode = 'none' | 'p10' | 'p12' | 'p15' | 'customPct' | 'customAmt';

@Component({
  selector: 'app-waiter-table-detail-page',
  imports: [RouterLink, DecimalPipe, PayModal],
  templateUrl: './waiter-table-detail-page.html',
  styleUrl: './waiter-table-detail-page.scss',
})
export class WaiterTableDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(WaiterOrderPointService);
  private readonly toast = inject(ToastService);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  readonly name = signal<string>(history.state?.name ?? '');
  /** Whether this order point is a protocol (comp/house) table. */
  readonly protocol = signal(false);
  /** Whether this order point is pay-later (settled by the waiter) vs pay-now. */
  readonly payLater = signal(true);
  /** Accepted payment methods for this order point (empty = all). */
  readonly acceptedMethods = signal<string[]>([]);
  /**
   * Non-protocol method buttons. Non-pay-later (pay-now) points are card only; pay-later points
   * offer their configured methods, or Cash + Card when none are configured.
   */
  readonly payButtons = computed(() => {
    if (!this.payLater()) {
      return ['CARD'];
    }
    const m = this.acceptedMethods();
    return m.length ? m : ['CASH', 'CARD'];
  });

  readonly bill = signal<BillLine[]>([]);
  readonly loadingOrders = signal(true);
  readonly ordersError = signal<string | null>(null);

  /** Unpaid lines grouped by product — the current bill. */
  readonly unpaidByProduct = computed<ProductLine[]>(() =>
    this.bill()
      .filter((b) => b.unpaidQty > 0)
      .map((b) => ({ menuItemId: b.menuItemId, name: b.name, price: b.price, qty: b.unpaidQty })),
  );

  /** Paid lines grouped by product. */
  readonly paidByProduct = computed<ProductLine[]>(() =>
    this.bill()
      .filter((b) => b.paidQty > 0)
      .map((b) => ({ menuItemId: b.menuItemId, name: b.name, price: b.price, qty: b.paidQty })),
  );
  // which list to show, persisted in the URL (?view=) so a refresh stays put
  readonly view = signal<'unpaid' | 'paid'>(
    this.route.snapshot.queryParamMap.get('view') === 'paid' ? 'paid' : 'unpaid',
  );
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
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { view: v === 'paid' ? 'paid' : null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  readonly orderedTotal = computed(() =>
    this.bill().reduce((s, b) => s + (b.price ?? 0) * (b.paidQty + b.unpaidQty), 0),
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

  /** Product whose quantity editor (opened by long-press) is shown, or null. */
  readonly editingId = signal<string | null>(null);
  readonly editingName = computed(() => {
    const id = this.editingId();
    return id ? (this.unpaidByProduct().find((p) => p.menuItemId === id)?.name ?? '') : '';
  });
  /** Most that can be moved for the edited product (its total unpaid quantity). */
  readonly editingMax = computed(() => {
    const id = this.editingId();
    return id ? (this.unpaidByProduct().find((p) => p.menuItemId === id)?.qty ?? 0) : 0;
  });
  /** Quantity currently selected to pay for the edited product. */
  readonly editingCurrent = computed(() => {
    const id = this.editingId();
    return id ? (this.paying()[id] ?? 0) : 0;
  });

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
          this.payLater.set(op.payLater);
          this.acceptedMethods.set(op.paymentMethods ?? []);
        }
      },
    });
    this.reload();
  }

  private reload(): void {
    this.loadingOrders.set(true);
    this.service.bill(this.id).subscribe({
      next: (bill) => {
        this.bill.set(bill);
        this.loadingOrders.set(false);
      },
      error: () => {
        this.ordersError.set('Could not load orders.');
        this.loadingOrders.set(false);
      },
    });
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
  /** Select the whole bill for payment (move every unpaid item to "Paying"). */
  moveAllToPaying(): void {
    const all: Record<string, number> = {};
    for (const p of this.unpaidByProduct()) {
      all[p.menuItemId] = p.qty;
    }
    this.paying.set(all);
  }
  /** Move everything back to the bill. */
  clearPaying(): void {
    this.paying.set({});
  }
  // long-press (any pointer): open a quantity editor for the pressed product
  private pressTimer: ReturnType<typeof setTimeout> | null = null;
  private clearPress(): void {
    if (this.pressTimer) {
      clearTimeout(this.pressTimer);
      this.pressTimer = null;
    }
  }
  openQtyEditor(id: string): void {
    this.editingId.set(id);
  }
  applyQty(value: number): void {
    const id = this.editingId();
    if (id) {
      this.setPayQty(id, value);
    }
    this.editingId.set(null);
  }
  cancelQtyEdit(): void {
    this.editingId.set(null);
  }
  /** Set an exact quantity to pay for a product, clamped to what's still unpaid. */
  setPayQty(id: string, value: number): void {
    const product = this.unpaidByProduct().find((p) => p.menuItemId === id);
    if (!product) {
      return;
    }
    let q = Math.floor(value);
    if (isNaN(q) || q < 0) {
      q = 0;
    }
    if (q > product.qty) {
      q = product.qty;
    }
    this.paying.update((m) => {
      const next = { ...m };
      if (q <= 0) {
        delete next[id];
      } else {
        next[id] = q;
      }
      return next;
    });
  }

  // mouse: native HTML5 drag-and-drop
  onDragStart(id: string, source: 'bill' | 'paying'): void {
    this.clearPress(); // a real drag started — not a long-press
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
    // long-press → quantity editor (works for both mouse hold and touch hold)
    this.clearPress();
    this.pressTimer = setTimeout(() => {
      this.pressTimer = null;
      if (this.touchDrag) {
        this.touchDrag.ghost?.remove();
        this.touchDrag = null;
      }
      this.justDragged = true; // suppress the click/drag that follows the release
      setTimeout(() => (this.justDragged = false), 400);
      this.openQtyEditor(id);
    }, 500);

    if (event.pointerType === 'mouse') {
      return; // mouse uses native DnD + click (plus the long-press timer above)
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
      this.clearPress(); // moved → it's a drag, not a long-press
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
    this.clearPress(); // released before the long-press fired → cancel it
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

  /** Full / protocol payment from the shared modal (it carries the tip). */
  onPayFull(e: { method: PaymentMethod; tip: number }): void {
    this.settle('FULL', e.method, e.tip, []);
  }

  /** Partial payment from the inline drag-drop modal (tip from this page's own tip controls). */
  pay(method: PaymentMethod): void {
    if (this.amount() <= 0) {
      return;
    }
    const items = this.payingList().map((p) => ({ menuItemId: p.menuItemId, quantity: p.pay }));
    this.settle('PARTIAL', method, this.computedTip(), items);
  }

  private settle(
    mode: 'FULL' | 'PARTIAL',
    method: PaymentMethod,
    tip: number,
    items: { menuItemId: string; quantity: number }[],
  ): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.payError.set(null);
    this.service.payLines(this.id, mode, method, tip, items).subscribe({
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
