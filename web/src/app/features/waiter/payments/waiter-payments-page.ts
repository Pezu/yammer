import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PaymentSummary, WaiterOrderPointService } from '../tables/waiter-order-point.service';

@Component({
  selector: 'app-waiter-payments-page',
  imports: [DecimalPipe],
  template: `
    <header class="page-head"><h1>Payments</h1></header>

    <section class="page-body">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (rows().length === 0) {
        <p class="state">No payments yet.</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th class="tcol">Table</th>
              <th class="acol">Amount</th>
              <th class="ccol">Type</th>
              <th>Tips</th>
              <th>Receipt</th>
            </tr>
          </thead>
          <tbody>
            @for (p of rows(); track p.id) {
              <tr [class]="rowClass(p)">
                <td class="tcol">{{ p.tableName }}</td>
                <td class="num acol">{{ p.amount | number: '1.2-2' }}</td>
                <td class="ccol">
                  @if (p.method === 'CASH') {
                    <svg class="mic" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-label="Cash"><rect x="2" y="6" width="20" height="12" rx="2"></rect><circle cx="12" cy="12" r="2.5"></circle></svg>
                  } @else {
                    <svg class="mic" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-label="Card"><rect x="2" y="5" width="20" height="14" rx="2"></rect><line x1="2" y1="10" x2="22" y2="10"></line></svg>
                  }
                </td>
                <td class="num">{{ p.tip | number: '1.2-2' }}</td>
                <td>{{ p.receiptNumber || '—' }}</td>
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
        min-height: 100%;
      }
      .page-head {
        padding: 1rem 1rem 0.5rem;
      }
      .page-head h1 {
        margin: 0;
        font-size: 0.8rem;
        font-weight: 500;
        text-transform: uppercase;
        letter-spacing: 0.06em;
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
      td.num,
      th:nth-child(2),
      th:nth-child(4) {
        text-align: right;
      }
      td.num {
        font-variant-numeric: tabular-nums;
      }
      .tcol {
        font-weight: 700;
      }
      .acol {
        width: 4.2rem;
      }
      .ccol {
        width: 2.4rem;
        text-align: center;
      }
      th.ccol {
        text-align: center;
      }
      .mic {
        vertical-align: middle;
        color: var(--muted);
      }
      .row-success td {
        background: rgba(22, 163, 74, 0.1);
      }
      .row-pending td {
        background: rgba(184, 130, 7, 0.1);
      }
      .row-failed td {
        background: rgba(234, 77, 77, 0.1);
      }
      .fail-note td {
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

  constructor() {
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

  rowClass(p: PaymentSummary): string {
    if (p.fiscalStatus === 'SUCCESS') return 'row-success';
    if (p.fiscalStatus === 'PENDING') return 'row-pending';
    if (p.fiscalStatus === 'FAILED') return 'row-failed';
    return '';
  }

  retry(p: PaymentSummary): void {
    if (this.retrying().has(p.id)) return;
    this.retrying.update((s) => new Set(s).add(p.id));
    this.service.retryFiscal(p.id).subscribe({
      next: () => this.retrying.update((s) => this.without(s, p.id)),
      error: () => this.retrying.update((s) => this.without(s, p.id)),
    });
  }

  private without(s: Set<string>, id: string): Set<string> {
    const next = new Set(s);
    next.delete(id);
    return next;
  }
}
