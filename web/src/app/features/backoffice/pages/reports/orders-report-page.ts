import { Component, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Order, OrderItem, OrderReportService } from './order.service';

@Component({
  selector: 'app-orders-report-page',
  imports: [],
  templateUrl: './orders-report-page.html',
  styleUrl: './orders-report-page.scss',
})
export class OrdersReportPage {
  private readonly service = inject(OrderReportService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly orders = signal<Order[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal(1);

  readonly pageSizes = [10, 50, 100];
  readonly pageSize = signal(10);
  readonly comboOpen = signal(false);

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.orders().length / this.pageSize())));
  readonly pagedOrders = computed(() => {
    const start = (this.page() - 1) * this.pageSize();
    return this.orders().slice(start, start + this.pageSize());
  });
  readonly range = computed(() => {
    const total = this.orders().length;
    if (total === 0) return '0';
    const start = (this.page() - 1) * this.pageSize() + 1;
    return `${start}–${Math.min(total, this.page() * this.pageSize())} of ${total}`;
  });

  // --- order detail (right panel, editable) ---
  readonly selected = signal<Order | null>(null);
  readonly detailTab = signal<'paid' | 'unpaid'>('unpaid');
  readonly editQty = signal<Map<string, number>>(new Map());
  readonly pendingDelete = signal(false);
  readonly saving = signal(false);

  readonly paidItems = computed(() => this.selected()?.items.filter((i) => i.paid) ?? []);
  readonly unpaidItems = computed(() => this.selected()?.items.filter((i) => !i.paid) ?? []);
  readonly canDelete = computed(() => this.selected()?.paid === 'NOT');
  readonly hasChanges = computed(() => {
    const sel = this.selected();
    if (!sel) return false;
    if (this.pendingDelete()) return true;
    const m = this.editQty();
    return sel.items.some((it) => !it.paid && (m.get(it.id) ?? it.quantity) !== it.quantity);
  });

  constructor() {
    this.service.list().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load orders.');
        this.loading.set(false);
      },
    });
  }

  selectOrder(o: Order): void {
    this.selected.set(o);
    this.pendingDelete.set(false);
    this.editQty.set(this.qtyMap(o));
  }

  qtyOf(it: OrderItem): number {
    return this.editQty().get(it.id) ?? it.quantity;
  }
  inc(it: OrderItem): void {
    this.editQty.update((m) => new Map(m).set(it.id, this.qtyOf(it) + 1));
  }
  dec(it: OrderItem): void {
    this.editQty.update((m) => new Map(m).set(it.id, Math.max(0, this.qtyOf(it) - 1)));
  }

  markDelete(): void {
    if (this.canDelete()) this.pendingDelete.set(true);
  }
  undoDelete(): void {
    this.pendingDelete.set(false);
  }

  save(): void {
    const sel = this.selected();
    if (!sel || this.saving() || !this.hasChanges()) return;
    this.saving.set(true);
    this.error.set(null);

    const items = sel.items
      .filter((it) => !it.paid)
      .map((it) => ({ id: it.id, quantity: this.editQty().get(it.id) ?? it.quantity }));
    // nothing would remain (no paid items, all unpaid removed) → delete the whole order
    const remaining = sel.items.filter((it) => it.paid).length + items.filter((i) => i.quantity > 0).length;

    if (this.pendingDelete() || remaining === 0) {
      this.commitDelete(sel);
      return;
    }

    this.service.updateItems(sel.id, items).subscribe({
      next: (updated) => {
        this.orders.update((list) => list.map((o) => (o.id === updated.id ? updated : o)));
        this.selected.set(updated);
        this.editQty.set(this.qtyMap(updated));
        this.saving.set(false);
      },
      error: () => {
        this.error.set('Could not save changes.');
        this.saving.set(false);
      },
    });
  }

  private commitDelete(sel: Order): void {
    this.service.deleteOrder(sel.id).subscribe({
      next: () => {
        this.orders.update((list) => list.filter((o) => o.id !== sel.id));
        this.selected.set(null);
        this.saving.set(false);
      },
      error: () => {
        this.error.set('Could not delete the order.');
        this.saving.set(false);
      },
    });
  }

  private qtyMap(o: Order): Map<string, number> {
    const m = new Map<string, number>();
    for (const it of o.items) if (!it.paid) m.set(it.id, it.quantity);
    return m;
  }

  html(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(value);
  }

  money(v: number): string {
    return Number.isInteger(v)
      ? v.toLocaleString('en-US')
      : v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  itemPrice(i: OrderItem, qty?: number): string {
    return this.money((i.price ?? 0) * (qty ?? i.quantity));
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ORDERED':
        return 'ORD';
      case 'READY':
        return 'RDY';
      case 'DELIVERED':
        return 'DEL';
      case 'CANCELED':
        return 'CAN';
      default:
        return status.slice(0, 3).toUpperCase();
    }
  }

  prev(): void {
    this.page.update((p) => Math.max(1, p - 1));
  }
  next(): void {
    this.page.update((p) => Math.min(this.totalPages(), p + 1));
  }

  toggleCombo(): void {
    this.comboOpen.update((o) => !o);
  }
  closeCombo(): void {
    this.comboOpen.set(false);
  }
  setPageSize(n: number): void {
    this.pageSize.set(n);
    this.page.set(1);
    this.comboOpen.set(false);
  }
}
