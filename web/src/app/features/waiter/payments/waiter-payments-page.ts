import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PaymentSummary, WaiterOrderPointService } from '../tables/waiter-order-point.service';

type StatusFilter = 'ALL' | 'SUCCESS' | 'PENDING' | 'FAILED';
const FILTER_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'SUCCESS', label: 'Success' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'FAILED', label: 'Failed' },
];

@Component({
  selector: 'app-waiter-payments-page',
  imports: [DecimalPipe],
  template: `
    <header class="page-head">
      <div class="view-combo">
        <button type="button" class="combo-trigger" (click)="toggleCombo()">
          <span>{{ filterLabel() }}</span>
          <svg class="caret" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>
        </button>
        @if (comboOpen()) {
          <div class="combo-backdrop" (click)="closeCombo()"></div>
          <div class="combo-menu">
            @for (o of options; track o.value) {
              <button type="button" class="combo-opt" [class.active]="filter() === o.value" (click)="select(o.value)">{{ o.label }}</button>
            }
          </div>
        }
      </div>
    </header>

    <section class="page-body">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (visibleRows().length === 0) {
        <p class="state">No payments.</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th class="tcol">Table</th>
              <th class="ccol">Type</th>
              <th class="acol">Amount</th>
              <th class="acol">Tips</th>
              <th class="scol">Status</th>
            </tr>
          </thead>
          <tbody>
            @for (p of visibleRows(); track p.id) {
              <tr>
                <td class="tcol">{{ p.tableName }}</td>
                <td class="ccol">
                  @if (p.method === 'CASH') {
                    <svg class="mic" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-label="Cash"><circle cx="8" cy="8" r="6"></circle><path d="M18.09 10.37A6 6 0 1 1 10.34 18"></path><path d="M7 6h1v4"></path><path d="m16.71 13.88.7.71-2.82 2.82"></path></svg>
                  } @else {
                    <svg class="mic" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-label="Card"><rect x="2" y="5" width="20" height="14" rx="2"></rect><line x1="2" y1="10" x2="22" y2="10"></line></svg>
                  }
                </td>
                <td class="num acol">{{ p.amount | number: '1.2-2' }}</td>
                <td class="num acol">{{ p.tip | number: '1.2-2' }}</td>
                <td class="scol">
                  <span class="dot"
                    [class.success]="p.fiscalStatus === 'SUCCESS'"
                    [class.pending]="p.fiscalStatus === 'PENDING'"
                    [class.failed]="p.fiscalStatus === 'FAILED'"
                    [attr.title]="p.fiscalStatus"></span>
                </td>
              </tr>
              @if (p.fiscalStatus === 'FAILED') {
                <tr class="row-failed fail-note">
                  <td colspan="5">
                    <div class="fail-row">
                      <span class="fail-msg">
                        Fiscal receipt not issued — the payment went through, but the receipt was not printed.
                      </span>
                      <button type="button" class="retry-btn" [disabled]="retrying().has(p.id)" (click)="retry(p)">
                        @if (retrying().has(p.id)) { Retrying… } @else { Retry }
                      </button>
                    </div>
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
        background: #fff;
        min-height: calc(100dvh - 56px); /* fill the content area below the 56px topbar */
      }
      .page-head {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        padding: 0.85rem 1rem 0.6rem;
      }
      .view-combo {
        position: relative;
      }
      .combo-trigger {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
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
      .combo-trigger .caret {
        color: currentColor;
      }
      .combo-trigger:active {
        background: rgba(52, 84, 209, 0.08);
      }
      .combo-backdrop {
        position: fixed;
        inset: 0;
        z-index: 30;
      }
      .combo-menu {
        position: absolute;
        top: calc(100% + 4px);
        right: 0;
        z-index: 31;
        min-width: 8rem;
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 10px;
        box-shadow: 0 0.5rem 1.5rem rgba(18, 27, 46, 0.15);
        overflow: hidden;
      }
      .combo-opt {
        display: block;
        width: 100%;
        padding: 0.65rem 0.9rem;
        font: inherit;
        font-size: 0.9rem;
        text-align: left;
        color: var(--text);
        background: none;
        border: none;
        cursor: pointer;
        -webkit-tap-highlight-color: transparent;
      }
      .combo-opt:active {
        background: var(--page-bg);
      }
      .combo-opt.active {
        color: var(--primary);
        font-weight: 700;
      }
      .page-body {
        padding: 0 0.25rem 1.5rem;
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
      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.78rem;
        table-layout: fixed;
      }
      th,
      td {
        padding: 0.5rem 0.35rem;
        text-align: left;
        border-bottom: 1px solid var(--border);
        overflow: hidden;
        text-overflow: ellipsis;
      }
      th {
        font-size: 0.6rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.02em;
        color: var(--muted);
      }
      td.num {
        text-align: center;
        font-variant-numeric: tabular-nums;
      }
      .tcol {
        width: 3.5rem;
        font-weight: 700;
      }
      .acol {
        width: 4.2rem;
        text-align: center;
      }
      .ccol {
        width: 2.4rem;
        text-align: center;
      }
      th.acol,
      th.ccol,
      th.scol {
        text-align: center;
      }
      .scol {
        width: 4.5rem;
        text-align: center;
      }
      .mic {
        vertical-align: middle;
        color: var(--muted);
      }
      .dot {
        display: inline-block;
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: var(--muted);
        vertical-align: middle;
      }
      .dot.success {
        background: #16a34a;
      }
      .dot.pending {
        background: #b88207;
      }
      .dot.failed {
        background: var(--danger);
      }
      .fail-note td {
        background: rgba(234, 77, 77, 0.1);
        border-bottom: 1px solid var(--border);
      }
      .fail-row {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        padding: 0.1rem 0;
      }
      .fail-msg {
        flex: 1;
        font-size: 0.72rem;
        color: var(--danger);
        line-height: 1.25;
      }
      .retry-btn {
        flex: none;
        padding: 0.3rem 0.7rem;
        font-size: 0.72rem;
        font-weight: 600;
        color: var(--danger);
        background: #fff;
        border: 1px solid var(--danger);
        border-radius: 6px;
        cursor: pointer;
      }
      .retry-btn:disabled {
        opacity: 0.55;
        cursor: default;
      }
    `,
  ],
})
export class WaiterPaymentsPage {
  private readonly service = inject(WaiterOrderPointService);

  readonly rows = signal<PaymentSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly retrying = signal<Set<string>>(new Set());

  readonly options = FILTER_OPTIONS;
  readonly filter = signal<StatusFilter>('FAILED');
  readonly comboOpen = signal(false);
  readonly filterLabel = computed(
    () => FILTER_OPTIONS.find((o) => o.value === this.filter())?.label ?? 'All',
  );
  readonly visibleRows = computed(() =>
    this.filter() === 'ALL'
      ? this.rows()
      : this.rows().filter((p) => p.fiscalStatus === this.filter()),
  );

  toggleCombo(): void {
    this.comboOpen.update((o) => !o);
  }
  closeCombo(): void {
    this.comboOpen.set(false);
  }
  select(value: StatusFilter): void {
    this.filter.set(value);
    this.comboOpen.set(false);
  }

  constructor() {
    this.load();
  }

  private load(): void {
    this.service.paymentsSummary().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load payments.');
        this.loading.set(false);
      },
    });
  }

  retry(p: PaymentSummary): void {
    if (this.retrying().has(p.id)) return;
    this.retrying.update((s) => new Set(s).add(p.id));
    this.service.retryFiscal(p.id).subscribe({
      // Fiscalization runs async on the bridge; give it a moment, then refresh so the
      // updated fiscal status (SUCCESS / still FAILED) shows without a manual reload.
      next: () =>
        setTimeout(() => {
          this.retrying.update((s) => this.without(s, p.id));
          this.load();
        }, 2500),
      error: () => this.retrying.update((s) => this.without(s, p.id)),
    });
  }

  private without(s: Set<string>, id: string): Set<string> {
    const next = new Set(s);
    next.delete(id);
    return next;
  }
}
