import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { TableStats, WaiterOrderPointService } from '../tables/waiter-order-point.service';

@Component({
  selector: 'app-waiter-statistics-page',
  imports: [DecimalPipe, NgTemplateOutlet],
  template: `
    <header class="page-head">
      <div class="view-combo">
        <button type="button" class="combo-trigger" (click)="toggleCombo()">
          <span>{{ view() === 'protocol' ? 'Protocol' : 'Paid' }}</span>
          <svg class="caret" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>
        </button>
        @if (comboOpen()) {
          <div class="combo-backdrop" (click)="closeCombo()"></div>
          <div class="combo-menu">
            <button type="button" class="combo-opt" [class.active]="view() === 'paid'" (click)="select('paid')">Paid</button>
            <button type="button" class="combo-opt" [class.active]="view() === 'protocol'" (click)="select('protocol')">Protocol</button>
          </div>
        }
      </div>
    </header>

    <section class="page-body">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (rows().length === 0) {
        <p class="state">No tables assigned.</p>
      } @else if (view() === 'paid') {
        @if (normalRows().length) {
          <div class="cards">
            <div class="card grand">
              <div class="card-head">All tables</div>
              <ng-container [ngTemplateOutlet]="statBlock" [ngTemplateOutletContext]="{ s: normalTotals() }"></ng-container>
            </div>
            @for (r of normalRows(); track r.orderPointId) {
              <div class="card">
                <div class="card-head">{{ r.name }}</div>
                <ng-container [ngTemplateOutlet]="statBlock" [ngTemplateOutletContext]="{ s: r }"></ng-container>
              </div>
            }
          </div>
        } @else {
          <p class="state">No paid tables.</p>
        }
      } @else {
        @if (protocolRows().length) {
          <div class="cards">
            <div class="card grand proto">
              <div class="card-head">All protocol</div>
              <ng-container [ngTemplateOutlet]="protoBlock" [ngTemplateOutletContext]="{ s: protocolTotals() }"></ng-container>
            </div>
            @for (r of protocolRows(); track r.orderPointId) {
              <div class="card proto">
                <div class="card-head">{{ r.name }}</div>
                <ng-container [ngTemplateOutlet]="protoBlock" [ngTemplateOutletContext]="{ s: r }"></ng-container>
              </div>
            }
          </div>
        } @else {
          <p class="state">No protocol tables.</p>
        }
      }
    </section>

    <ng-template #statBlock let-s="s">
      <div class="grp">
        <div class="row"><span>Paid card</span><span>{{ s.paidCard | number: '1.2-2' }}</span></div>
        <div class="row"><span>Paid cash</span><span>{{ s.paidCash | number: '1.2-2' }}</span></div>
        <div class="row total"><span>Total</span><span>{{ s.paidCard + s.paidCash | number: '1.2-2' }}</span></div>
      </div>
      <div class="grp">
        <div class="row"><span>Tip card</span><span>{{ s.tipCard | number: '1.2-2' }}</span></div>
        <div class="row"><span>Tip cash</span><span>{{ s.tipCash | number: '1.2-2' }}</span></div>
        <div class="row total"><span>Tip</span><span>{{ s.tipCard + s.tipCash | number: '1.2-2' }}</span></div>
      </div>
      <div class="row unpaid"><span>Unpaid</span><span>{{ s.unpaid | number: '1.2-2' }}</span></div>
    </ng-template>

    <ng-template #protoBlock let-s="s">
      <div class="row"><span>Comped</span><span>{{ s.settled | number: '1.2-2' }}</span></div>
      <div class="row unpaid"><span>Unpaid</span><span>{{ s.unpaid | number: '1.2-2' }}</span></div>
    </ng-template>
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
        padding: 0.25rem 1rem 1.5rem;
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
      .cards {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .card {
        border: 1px solid var(--border);
        border-radius: 12px;
        padding: 0.7rem 0.9rem;
      }
      .card.grand {
        border-color: var(--primary);
        background: rgba(52, 84, 209, 0.04);
      }
      .card.proto {
        border-color: #d9b08c;
      }
      .card.grand.proto {
        border-color: #c98a4b;
        background: rgba(201, 138, 75, 0.06);
      }
      .card-head {
        font-weight: 800;
        font-size: 0.95rem;
        text-transform: uppercase;
        letter-spacing: 0.03em;
        margin-bottom: 0.4rem;
      }
      .grp {
        padding: 0.25rem 0;
      }
      .grp + .grp {
        border-top: 1px solid var(--border);
      }
      .row {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        padding: 0.2rem 0;
        font-size: 0.85rem;
      }
      .row span:first-child {
        color: var(--muted);
      }
      .row span:last-child {
        font-variant-numeric: tabular-nums;
      }
      .row.total span {
        font-weight: 800;
        color: var(--text);
      }
      .row.unpaid {
        margin-top: 0.35rem;
        padding-top: 0.45rem;
        border-top: 1px solid var(--border);
        font-weight: 700;
      }
      .row.unpaid span:first-child {
        color: var(--text);
      }
    `,
  ],
})
export class WaiterStatisticsPage {
  private readonly service = inject(WaiterOrderPointService);

  readonly rows = signal<TableStats[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly view = signal<'paid' | 'protocol'>('paid');
  readonly comboOpen = signal(false);
  toggleCombo(): void {
    this.comboOpen.update((o) => !o);
  }
  closeCombo(): void {
    this.comboOpen.set(false);
  }
  select(v: 'paid' | 'protocol'): void {
    this.view.set(v);
    this.comboOpen.set(false);
  }

  readonly normalRows = computed(() => this.rows().filter((r) => !r.protocol));
  readonly protocolRows = computed(() => this.rows().filter((r) => r.protocol));

  readonly normalTotals = computed(() => {
    const r = this.normalRows();
    return {
      paidCard: r.reduce((s, x) => s + x.paidCard, 0),
      paidCash: r.reduce((s, x) => s + x.paidCash, 0),
      tipCard: r.reduce((s, x) => s + x.tipCard, 0),
      tipCash: r.reduce((s, x) => s + x.tipCash, 0),
      unpaid: r.reduce((s, x) => s + x.unpaid, 0),
    };
  });
  readonly protocolTotals = computed(() => {
    const r = this.protocolRows();
    return {
      settled: r.reduce((s, x) => s + x.settled, 0),
      unpaid: r.reduce((s, x) => s + x.unpaid, 0),
    };
  });

  constructor() {
    this.service.stats().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load statistics.');
        this.loading.set(false);
      },
    });
  }
}
