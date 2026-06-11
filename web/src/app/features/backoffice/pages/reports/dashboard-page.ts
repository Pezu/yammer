import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import {
  OrderReportService,
  ProductReportRow,
  TableReportRow,
  WaiterReportRow,
  WaiterTableRow,
} from './order.service';
import { SalesWidget } from './sales-widget';

const PAGE_SIZE = 8;

@Component({
  selector: 'app-dashboard-page',
  imports: [DecimalPipe, SalesWidget],
  template: `
    <header class="page-header">
      <h1>Dashboard</h1>
    </header>

    <section class="page-body">
      <!-- Tables (2/3): ordered / paid / remaining per order point -->
      <div class="widget col-2">
        <div class="widget-head">
          <h5 class="widget-title">Tables</h5>
        </div>
        <div class="tab-body">
          @if (tablesLoading()) {
            <p class="state">Loading…</p>
          } @else if (tables().length === 0) {
            <p class="state">No orders yet.</p>
          } @else {
            <table class="grid tight">
              <thead>
                <tr>
                  <th>Table</th>
                  <th class="num">Ordered</th>
                  <th class="num">Ord. paid</th>
                  <th class="num div">Ord. prot.</th>
                  <th class="num">Cash</th>
                  <th class="num">Card</th>
                  <th class="num div">Protocol</th>
                  <th class="num">Remaining</th>
                  <th class="num">Rem. prot.</th>
                </tr>
              </thead>
              <tbody>
                @for (t of pagedTables(); track t.name) {
                  <tr>
                    <td><span class="op" [innerHTML]="t.name"></span></td>
                    <td class="num">{{ t.ordered | number: '1.2-2' }}</td>
                    <td class="num">{{ t.orderedPaid | number: '1.2-2' }}</td>
                    <td class="num div">{{ t.orderedProtocol | number: '1.2-2' }}</td>
                    <td class="num">{{ t.paidCash | number: '1.2-2' }}</td>
                    <td class="num">{{ t.paidCard | number: '1.2-2' }}</td>
                    <td class="num div">{{ t.protocol | number: '1.2-2' }}</td>
                    <td class="num" [class.rem]="t.remaining > 0">{{ t.remaining | number: '1.2-2' }}</td>
                    <td class="num" [class.rem]="t.remainingProtocol > 0">{{ t.remainingProtocol | number: '1.2-2' }}</td>
                  </tr>
                }
              </tbody>
              <tfoot>
                <tr class="total-row">
                  <td>Total</td>
                  <td class="num">{{ tableTotals().ordered | number: '1.2-2' }}</td>
                  <td class="num">{{ tableTotals().orderedPaid | number: '1.2-2' }}</td>
                  <td class="num div">{{ tableTotals().orderedProtocol | number: '1.2-2' }}</td>
                  <td class="num">{{ tableTotals().paidCash | number: '1.2-2' }}</td>
                  <td class="num">{{ tableTotals().paidCard | number: '1.2-2' }}</td>
                  <td class="num div">{{ tableTotals().protocol | number: '1.2-2' }}</td>
                  <td class="num">{{ tableTotals().remaining | number: '1.2-2' }}</td>
                  <td class="num">{{ tableTotals().remainingProtocol | number: '1.2-2' }}</td>
                </tr>
              </tfoot>
            </table>

            <div class="pager">
              <span class="range">{{ tableRange() }}</span>
              <div class="pager-btns">
                <button type="button" (click)="prevTable()" [disabled]="tablePage() <= 1">‹</button>
                <span class="page">{{ tablePage() }} / {{ tablePages() }}</span>
                <button type="button" (click)="nextTable()" [disabled]="tablePage() >= tablePages()">›</button>
              </div>
            </div>
          }
        </div>
      </div>

      <!-- Sales (1/3) -->
      <app-sales-widget class="col-1" />

      <!-- Products (1/3): paginated product list with quantities -->
      <div class="widget col-1">
        <div class="widget-head">
          <h5 class="widget-title">Products</h5>
        </div>
        <div class="tab-body">
          @if (productsLoading()) {
            <p class="state">Loading…</p>
          } @else if (error()) {
            <p class="state err">{{ error() }}</p>
          } @else if (products().length === 0) {
            <p class="state">No products ordered yet.</p>
          } @else {
            <table class="grid">
              <thead>
                <tr><th>Product</th><th class="num">Qty</th></tr>
              </thead>
              <tbody>
                @for (p of pagedProducts(); track p.name) {
                  <tr>
                    <td><span [innerHTML]="p.name"></span></td>
                    <td class="num">{{ p.quantity | number: '1.0-0' }}</td>
                  </tr>
                }
              </tbody>
            </table>

            <div class="pager">
              <span class="range">{{ productRange() }}</span>
              <div class="pager-btns">
                <button type="button" (click)="prevProduct()" [disabled]="productPage() <= 1">‹</button>
                <span class="page">{{ productPage() }} / {{ productPages() }}</span>
                <button type="button" (click)="nextProduct()" [disabled]="productPage() >= productPages()">›</button>
              </div>
            </div>
          }
        </div>
      </div>

      <!-- Waiters (1/3): orders + sales per waiter -->
      <div class="widget col-1">
        <div class="widget-head">
          <h5 class="widget-title">Waiters</h5>
        </div>
        <div class="tab-body">
          @if (waitersLoading()) {
            <p class="state">Loading…</p>
          } @else if (waiters().length === 0) {
            <p class="state">No orders yet.</p>
          } @else {
            <table class="grid">
              <thead>
                <tr><th>Waiter</th><th class="num">Orders</th><th class="num">Sales</th></tr>
              </thead>
              <tbody>
                @for (w of waiters(); track w.name) {
                  <tr class="clickable" (click)="toggleWaiter(w.name)">
                    <td>
                      <span class="caret" [class.open]="expandedWaiter() === w.name">▸</span>
                      {{ w.name }}
                    </td>
                    <td class="num">{{ w.orders | number: '1.0-0' }}</td>
                    <td class="num">{{ w.sales | number: '1.2-2' }}</td>
                  </tr>
                  @if (expandedWaiter() === w.name) {
                    <tr class="sub-row">
                      <td colspan="3">
                        <table class="subgrid">
                          <thead>
                            <tr>
                              <th>Table</th>
                              <th class="num">Ordered</th>
                              <th class="num">Paid</th>
                              <th class="num">Tip</th>
                              <th class="num">Prot.</th>
                            </tr>
                          </thead>
                          <tbody>
                            @for (t of tablesFor(w.name); track t.table) {
                              <tr>
                                <td>{{ t.table }}</td>
                                <td class="num">{{ t.ordered | number: '1.2-2' }}</td>
                                <td class="num">{{ t.paid | number: '1.2-2' }}</td>
                                <td class="num">{{ t.tip | number: '1.2-2' }}</td>
                                <td class="num">{{ t.protocol | number: '1.2-2' }}</td>
                              </tr>
                            } @empty {
                              <tr><td colspan="5" class="sub-empty">No tables.</td></tr>
                            }
                          </tbody>
                        </table>
                      </td>
                    </tr>
                  }
                }
              </tbody>
              <tfoot>
                <tr class="total-row">
                  <td>Total</td>
                  <td class="num">{{ waiterTotals().orders | number: '1.0-0' }}</td>
                  <td class="num">{{ waiterTotals().sales | number: '1.2-2' }}</td>
                </tr>
              </tfoot>
            </table>
          }
        </div>
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1.5rem 2rem;
        border-bottom: 1px solid var(--border);
        background: #fff;
      }
      .page-header h1 {
        margin: 0;
        font-size: 1.3rem;
        font-weight: 800;
      }
      .page-body {
        padding: 1.5rem 2rem;
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 1.5rem;
        align-items: start;
      }
      /* widget widths: 1/3, 2/3, 3/3 of the row */
      .col-1 {
        grid-column: span 1;
      }
      .col-2 {
        grid-column: span 2;
      }
      .col-3 {
        grid-column: span 3;
      }
      .widget {
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 10px;
        overflow: hidden;
      }
      .widget-head {
        padding: 1rem 1.25rem;
        border-bottom: 1px solid var(--border);
      }
      .widget-title {
        margin: 0;
        font-size: 1rem;
        font-weight: 700;
      }
      .tab-body {
        padding: 0.5rem 0 0;
      }
      .state {
        margin: 1.5rem 0;
        text-align: center;
        color: var(--muted);
        font-size: 0.9rem;
      }
      .state.err {
        color: var(--danger);
      }
      .grid {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.85rem;
      }
      .grid th,
      .grid td {
        padding: 0.55rem 1.25rem;
        text-align: left;
        border-bottom: 1px solid var(--border);
      }
      .grid th {
        font-size: 0.66rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.03em;
        color: var(--muted);
      }
      .grid td.num,
      .grid th.num {
        text-align: right;
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
      }
      .op {
        font-weight: 600;
      }
      .grid tfoot .total-row td {
        font-weight: 700;
        border-top: 2px solid var(--border);
        border-bottom: none;
      }
      .grid td.rem {
        color: var(--danger);
      }
      .grid tr.clickable {
        cursor: pointer;
      }
      .grid tr.clickable:hover td {
        background: var(--page-bg);
      }
      .caret {
        display: inline-block;
        margin-right: 0.25rem;
        font-size: 0.7rem;
        color: var(--muted);
        transition: transform 0.12s ease-in-out;
      }
      .caret.open {
        transform: rotate(90deg);
      }
      .sub-row > td {
        padding: 0;
        background: var(--page-bg);
      }
      .subgrid {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.66rem;
      }
      .subgrid th,
      .subgrid td {
        padding: 0.35rem 0.5rem;
        text-align: left;
        border-bottom: 1px solid var(--border);
        white-space: nowrap;
      }
      .subgrid th {
        font-size: 0.58rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.02em;
        color: var(--muted);
      }
      .subgrid td.num,
      .subgrid th.num {
        text-align: right;
        font-variant-numeric: tabular-nums;
      }
      .sub-empty {
        text-align: center;
        color: var(--muted);
      }
      /* tighter cells so the wide money table fits */
      .grid.tight th,
      .grid.tight td {
        padding: 0.5rem 0.4rem;
        font-size: 0.7rem;
      }
      .grid.tight th:first-child,
      .grid.tight td:first-child {
        padding-left: 1rem;
      }
      .grid.tight th:last-child,
      .grid.tight td:last-child {
        padding-right: 1rem;
      }
      /* vertical separators between column groups */
      .grid .div {
        border-right: 1px solid var(--border);
      }
      .pager {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.7rem 1.25rem;
      }
      .range {
        font-size: 0.75rem;
        color: var(--muted);
      }
      .pager-btns {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
      }
      .pager-btns .page {
        font-size: 0.78rem;
        color: var(--muted);
        font-variant-numeric: tabular-nums;
      }
      .pager-btns button {
        width: 28px;
        height: 28px;
        font-size: 1rem;
        line-height: 1;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 6px;
        cursor: pointer;
      }
      .pager-btns button:disabled {
        opacity: 0.4;
        cursor: default;
      }

      /* Mobile optimization — only when rendered in the watcher (.watcher host class),
         so the backoffice dashboard is never affected. */
      @media (max-width: 768px) {
        :host(.watcher) .page-header {
          padding: 1rem;
        }
        :host(.watcher) .page-header h1 {
          font-size: 1.1rem;
        }
        :host(.watcher) .page-body {
          grid-template-columns: 1fr;
          padding: 0.75rem;
          gap: 0.75rem;
        }
        :host(.watcher) .col-1,
        :host(.watcher) .col-2,
        :host(.watcher) .col-3 {
          grid-column: span 1;
        }
        /* wide tables (e.g. Tables widget) scroll horizontally instead of squishing */
        :host(.watcher) .tab-body {
          overflow-x: auto;
          -webkit-overflow-scrolling: touch;
        }
        :host(.watcher) .grid {
          min-width: max-content;
        }
        :host(.watcher) .grid th,
        :host(.watcher) .grid td {
          padding: 0.45rem 0.6rem;
        }
        :host(.watcher) .grid.tight th,
        :host(.watcher) .grid.tight td {
          font-size: 0.72rem;
        }
      }
    `,
  ],
})
export class DashboardPage {
  private readonly service = inject(OrderReportService);

  readonly error = signal<string | null>(null);

  // --- Products ---
  readonly products = signal<ProductReportRow[]>([]);
  readonly productsLoading = signal(true);
  readonly productPage = signal(1);
  readonly productPages = computed(() => Math.max(1, Math.ceil(this.products().length / PAGE_SIZE)));
  readonly pagedProducts = computed(() => {
    const start = (this.productPage() - 1) * PAGE_SIZE;
    return this.products().slice(start, start + PAGE_SIZE);
  });
  readonly productRange = computed(() => this.rangeLabel(this.productPage(), this.products().length));

  // --- Tables ---
  readonly tables = signal<TableReportRow[]>([]);
  readonly tablesLoading = signal(true);
  readonly tablePage = signal(1);
  readonly tablePages = computed(() => Math.max(1, Math.ceil(this.tables().length / PAGE_SIZE)));
  readonly pagedTables = computed(() => {
    const start = (this.tablePage() - 1) * PAGE_SIZE;
    return this.tables().slice(start, start + PAGE_SIZE);
  });
  readonly tableRange = computed(() => this.rangeLabel(this.tablePage(), this.tables().length));
  readonly tableTotals = computed(() =>
    this.tables().reduce(
      (acc, t) => ({
        ordered: acc.ordered + t.ordered,
        orderedPaid: acc.orderedPaid + t.orderedPaid,
        orderedProtocol: acc.orderedProtocol + t.orderedProtocol,
        paidCash: acc.paidCash + t.paidCash,
        paidCard: acc.paidCard + t.paidCard,
        protocol: acc.protocol + t.protocol,
        remaining: acc.remaining + t.remaining,
        remainingProtocol: acc.remainingProtocol + t.remainingProtocol,
      }),
      {
        ordered: 0,
        orderedPaid: 0,
        orderedProtocol: 0,
        paidCash: 0,
        paidCard: 0,
        protocol: 0,
        remaining: 0,
        remainingProtocol: 0,
      },
    ),
  );

  // --- Waiters ---
  readonly waiters = signal<WaiterReportRow[]>([]);
  readonly waitersLoading = signal(true);
  readonly waiterTables = signal<WaiterTableRow[]>([]);
  readonly expandedWaiter = signal<string | null>(null);
  readonly waiterTotals = computed(() =>
    this.waiters().reduce(
      (acc, w) => ({ orders: acc.orders + w.orders, sales: acc.sales + w.sales }),
      { orders: 0, sales: 0 },
    ),
  );

  toggleWaiter(name: string): void {
    this.expandedWaiter.update((c) => (c === name ? null : name));
  }
  tablesFor(name: string): WaiterTableRow[] {
    return this.waiterTables().filter((r) => r.waiter === name);
  }

  constructor() {
    this.service.products().subscribe({
      next: (rows) => {
        this.products.set(rows);
        this.productsLoading.set(false);
      },
      error: () => {
        this.error.set('Failed to load products.');
        this.productsLoading.set(false);
      },
    });
    this.service.tablesReport().subscribe({
      next: (rows) => {
        this.tables.set(rows);
        this.tablesLoading.set(false);
      },
      error: () => this.tablesLoading.set(false),
    });
    this.service.waitersReport().subscribe({
      next: (rows) => {
        this.waiters.set(rows);
        this.waitersLoading.set(false);
      },
      error: () => this.waitersLoading.set(false),
    });
    this.service.waiterTablesReport().subscribe({
      next: (rows) => this.waiterTables.set(rows),
      error: () => {
        /* drill-down unavailable; top-level still works */
      },
    });
  }


  prevProduct(): void {
    this.productPage.update((p) => Math.max(1, p - 1));
  }
  nextProduct(): void {
    this.productPage.update((p) => Math.min(this.productPages(), p + 1));
  }
  prevTable(): void {
    this.tablePage.update((p) => Math.max(1, p - 1));
  }
  nextTable(): void {
    this.tablePage.update((p) => Math.min(this.tablePages(), p + 1));
  }

  private rangeLabel(page: number, total: number): string {
    if (total === 0) return '0';
    const start = (page - 1) * PAGE_SIZE + 1;
    const end = Math.min(total, page * PAGE_SIZE);
    return `${start}–${end} of ${total}`;
  }
}
