import { Component, computed, inject, signal } from '@angular/core';
import {
  EventOption,
  Order,
  OrderItem,
  OrderPointOption,
  OrderReportService,
} from './order.service';
import { ComboOption, FilterCombo } from './filter-combo';

@Component({
  selector: 'app-orders-report-page',
  imports: [FilterCombo],
  templateUrl: './orders-report-page.html',
  styleUrl: './orders-report-page.scss',
})
export class OrdersReportPage {
  private readonly service = inject(OrderReportService);

  // `orders` holds the CURRENT page only; `total` is the server-side row count.
  readonly orders = signal<Order[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal(1);

  readonly pageSizes = [10, 50, 100];
  readonly pageSize = signal(10);
  readonly comboOpen = signal(false);

  // --- event filter ---
  readonly events = signal<EventOption[]>([]);
  readonly eventId = signal<string>(''); // '' = all events
  readonly eventComboOpen = signal(false);
  readonly eventSearch = signal('');
  readonly eventName = computed(
    () => this.events().find((e) => e.id === this.eventId())?.name ?? 'All events',
  );
  readonly eventOptions = computed(() => {
    const q = this.eventSearch().trim().toLowerCase();
    const all = this.events();
    return q ? all.filter((e) => e.name.toLowerCase().includes(q)) : all;
  });

  // --- column filters ---
  readonly fOrderNo = signal('');
  readonly fOrderPointId = signal('');
  readonly fWaiter = signal('');
  readonly fPaid = signal('');
  readonly fStatus = signal('');
  readonly orderPointOptions = signal<OrderPointOption[]>([]);
  readonly waiterOptions = signal<string[]>([]);
  readonly orderPointComboOptions = computed<ComboOption[]>(() =>
    this.orderPointOptions().map((o) => ({ value: o.id, label: o.name })),
  );
  readonly waiterComboOptions = computed<ComboOption[]>(() =>
    this.waiterOptions().map((w) => ({ value: w, label: w })),
  );
  readonly statusOptions: ComboOption[] = [
    { value: 'ORDERED', label: 'Ordered' },
    { value: 'READY', label: 'Ready' },
    { value: 'DELIVERED', label: 'Delivered' },
    { value: 'CANCELED', label: 'Canceled' },
  ];
  readonly paidOptions: ComboOption[] = [
    { value: 'NOT', label: 'Not paid' },
    { value: 'PAR', label: 'Partial' },
    { value: 'PAID', label: 'Paid' },
  ];

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  readonly pagedOrders = computed(() => this.orders());
  readonly range = computed(() => {
    const total = this.total();
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
    this.loadEvents();
    this.loadFilterOptions();
    this.load();
  }

  private loadEvents(): void {
    this.service.listEvents().subscribe({
      next: (events) => this.events.set(events),
      error: () => {
        /* a failed event list just leaves the filter showing "All events" */
      },
    });
  }

  private loadFilterOptions(): void {
    this.service.filterOptions(this.eventId() || null).subscribe({
      next: (o) => {
        this.orderPointOptions.set(o.orderPoints);
        this.waiterOptions.set(o.waiters);
      },
      error: () => {
        this.orderPointOptions.set([]);
        this.waiterOptions.set([]);
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.service
      .listPaged(this.page() - 1, this.pageSize(), {
        eventId: this.eventId() || null,
        orderNo: this.fOrderNo() ? Number(this.fOrderNo()) : null,
        orderPointId: this.fOrderPointId() || null,
        waiter: this.fWaiter() || null,
        status: this.fStatus() || null,
        paid: this.fPaid() || null,
      })
      .subscribe({
        next: (res) => {
          this.orders.set(res.content);
          this.total.set(res.total);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Failed to load orders.');
          this.loading.set(false);
        },
      });
  }

  // --- filter setters: each resets to page 1 and reloads ---
  setOrderNo(v: string): void {
    this.fOrderNo.set(v.trim());
    this.applyFilter();
  }
  setOrderPoint(v: string): void {
    this.fOrderPointId.set(v);
    this.applyFilter();
  }
  setWaiter(v: string): void {
    this.fWaiter.set(v);
    this.applyFilter();
  }
  setPaid(v: string): void {
    this.fPaid.set(v);
    this.applyFilter();
  }
  setStatus(v: string): void {
    this.fStatus.set(v);
    this.applyFilter();
  }
  private applyFilter(): void {
    this.page.set(1);
    this.selected.set(null);
    this.load();
  }
  private resetFilters(): void {
    this.fOrderNo.set('');
    this.fOrderPointId.set('');
    this.fWaiter.set('');
    this.fPaid.set('');
    this.fStatus.set('');
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
        this.selected.set(null);
        this.saving.set(false);
        this.load(); // refresh the current page (and total) after removal
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

  money(v: number): string {
    return Number.isInteger(v)
      ? v.toLocaleString('en-US')
      : v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  itemPrice(i: OrderItem, qty?: number): string {
    return this.money((i.price ?? 0) * (qty ?? i.quantity));
  }

  /** Display name of an order's event (null when unset or not in the loaded list). */
  eventNameOf(o: Order): string | null {
    if (!o.eventId) return null;
    return this.events().find((e) => e.id === o.eventId)?.name ?? null;
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
    if (this.page() <= 1) return;
    this.page.update((p) => Math.max(1, p - 1));
    this.load();
  }
  next(): void {
    if (this.page() >= this.totalPages()) return;
    this.page.update((p) => Math.min(this.totalPages(), p + 1));
    this.load();
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
    this.load();
  }

  toggleEventCombo(): void {
    this.eventComboOpen.update((o) => !o);
    if (this.eventComboOpen()) this.eventSearch.set('');
  }
  closeEventCombo(): void {
    this.eventComboOpen.set(false);
  }
  selectEvent(id: string): void {
    if (this.eventId() === id) {
      this.closeEventCombo();
      return;
    }
    this.eventId.set(id);
    this.resetFilters(); // order points / waiters differ per event
    this.page.set(1);
    this.selected.set(null);
    this.eventComboOpen.set(false);
    this.loadFilterOptions();
    this.load();
  }
}
