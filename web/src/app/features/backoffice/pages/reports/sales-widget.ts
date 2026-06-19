import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { OrderReportService, SalesIntervalRow, SalesSummary } from './order.service';

interface Bar {
  d: string;
  title: string;
}
interface ChartSeries {
  name: string;
  color: string;
  line: string;
  bars: Bar[];
}
interface GridLine {
  y: number;
  label: string;
}
interface XTick {
  x: number;
  label: string;
}
interface ChartModel {
  series: ChartSeries[];
  grid: GridLine[];
  xticks: XTick[];
  plotLeft: number;
  plotRight: number;
  plotBottom: number;
}

type Tab = 'summary' | 'amount' | 'orders';

const VW = 820;
const VH = 300;
const PAD_L = 52;
const PAD_R = 14;
const PAD_T = 14;
const PAD_B = 30;

@Component({
  selector: 'app-sales-widget',
  imports: [DecimalPipe],
  template: `
    <div class="widget">
      <div class="widget-head"><h5 class="widget-title">Sales</h5></div>

      <ul class="tabs">
        <li>
          <button type="button" class="tab" [class.active]="tab() === 'summary'" (click)="setTab('summary')">
            Summary
          </button>
        </li>
        <li>
          <button type="button" class="tab" [class.active]="tab() === 'amount'" (click)="setTab('amount')">
            Amount
          </button>
        </li>
        <li>
          <button type="button" class="tab" [class.active]="tab() === 'orders'" (click)="setTab('orders')">
            Orders
          </button>
        </li>
      </ul>

      <div class="tab-body">
        @if (loading()) {
          <p class="state">Loading…</p>
        } @else if (error()) {
          <p class="state err">{{ error() }}</p>
        } @else if (rows().length === 0) {
          <p class="state">No sales data yet.</p>
        } @else if (tab() === 'summary') {
          <dl class="summary">
            <div class="row total"><dt>Total sales</dt><dd>{{ summary().totalSales | number: '1.2-2' }}</dd></div>
            <div class="row"><dt>Paid <span class="hint">(tips excl.)</span></dt><dd>{{ summary().totalPaid | number: '1.2-2' }}</dd></div>
            <div class="row"><dt>Settled — protocol</dt><dd>{{ summary().totalProtocol | number: '1.2-2' }}</dd></div>
            <div class="row rem"><dt>Remaining to pay</dt><dd>{{ summary().remainingToPay | number: '1.2-2' }}</dd></div>
            <div class="row rem"><dt>Remaining — protocol</dt><dd>{{ summary().remainingProtocol | number: '1.2-2' }}</dd></div>
          </dl>
        } @else if (tab() === 'amount') {
          <div class="legend">
            <span class="lg"><i class="dot" style="background:#3454d1"></i>Ordered
              <b>{{ totalOrdered() | number: '1.2-2' }}</b></span>
            <span class="lg"><i class="dot" style="background:#25b865"></i>Paid
              <b>{{ totalPaid() | number: '1.2-2' }}</b></span>
            <span class="lg"><i class="dot" style="background:#e49e3d"></i>Paid + Protocol
              <b>{{ totalPaid() + totalProtocol() | number: '1.2-2' }}</b></span>
          </div>
          <svg class="chart" [attr.viewBox]="'0 0 ' + vw + ' ' + vh" role="img">
            @for (g of amountChart().grid; track g.y) {
              <line class="grid" [attr.x1]="amountChart().plotLeft" [attr.x2]="amountChart().plotRight" [attr.y1]="g.y" [attr.y2]="g.y" />
              <text class="y-lbl" [attr.x]="amountChart().plotLeft - 8" [attr.y]="g.y + 3">{{ g.label }}</text>
            }
            @for (s of amountChart().series; track s.name) {
              <path [attr.d]="s.line" fill="none" [attr.stroke]="s.color" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" />
            }
            @for (t of amountChart().xticks; track t.x) {
              <text class="x-lbl" [attr.x]="t.x" [attr.y]="amountChart().plotBottom + 18">{{ t.label }}</text>
            }
          </svg>
        } @else {
          <div class="legend">
            <span class="lg"><i class="dot" style="background:#e49e3d"></i>Orders / 10 min
              <b>{{ totalOrders() | number: '1.0-0' }}</b></span>
          </div>
          <svg class="chart" [attr.viewBox]="'0 0 ' + vw + ' ' + vh" role="img">
            @for (g of ordersChart().grid; track g.y) {
              <line class="grid" [attr.x1]="ordersChart().plotLeft" [attr.x2]="ordersChart().plotRight" [attr.y1]="g.y" [attr.y2]="g.y" />
              <text class="y-lbl" [attr.x]="ordersChart().plotLeft - 8" [attr.y]="g.y + 3">{{ g.label }}</text>
            }
            @for (s of ordersChart().series; track s.name) {
              @for (b of s.bars; track $index) {
                <path class="bar" [attr.d]="b.d" [attr.fill]="s.color"><title>{{ b.title }}</title></path>
              }
            }
            @for (t of ordersChart().xticks; track t.x) {
              <text class="x-lbl" [attr.x]="t.x" [attr.y]="ordersChart().plotBottom + 18">{{ t.label }}</text>
            }
          </svg>
        }
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
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
      .tabs {
        display: flex;
        list-style: none;
        margin: 0;
        padding: 0;
        border-bottom: 1px solid var(--border);
      }
      .tabs li {
        flex: 1;
      }
      .tab {
        width: 100%;
        padding: 0.85rem 0.5rem;
        font: inherit;
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--muted);
        background: none;
        border: none;
        border-bottom: 2px solid transparent;
        cursor: pointer;
      }
      .tab.active {
        color: var(--primary);
        border-bottom-color: var(--primary);
      }
      .tab-body {
        padding: 1rem 1.25rem 1.25rem;
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
      .summary {
        margin: 0;
        font-size: 0.9rem;
      }
      .summary .row {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.6rem 0.25rem;
        border-bottom: 1px solid var(--border);
      }
      .summary .row:last-child {
        border-bottom: none;
      }
      .summary dt {
        margin: 0;
        color: var(--muted);
      }
      .summary dt .hint {
        font-size: 0.75rem;
      }
      .summary dd {
        margin: 0;
        font-weight: 400;
        font-variant-numeric: tabular-nums;
      }
      .summary .total dt {
        color: var(--text);
        font-weight: 800;
        font-size: 1rem;
      }
      .summary .total dd {
        color: var(--text);
        font-size: 1rem;
      }
      .summary .rem dd {
        color: var(--danger);
      }
      .legend {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 1rem;
        margin-bottom: 0.75rem;
      }
      .lg {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.8rem;
        color: var(--muted);
      }
      .lg b {
        color: var(--text);
        font-weight: 700;
        font-variant-numeric: tabular-nums;
      }
      .dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
      }
      .chart {
        width: 100%;
        height: auto;
        display: block;
        overflow: visible;
      }
      .grid {
        stroke: var(--border);
        stroke-width: 1;
        stroke-dasharray: 3 3;
      }
      .y-lbl {
        fill: var(--muted);
        font-size: 11px;
        text-anchor: end;
      }
      .x-lbl {
        fill: var(--muted);
        font-size: 11px;
        text-anchor: middle;
      }
      .bar {
        transition: opacity 0.15s ease-in-out;
        cursor: pointer;
      }
      .bar:hover {
        opacity: 0.75;
      }
    `,
  ],
})
export class SalesWidget {
  private readonly service = inject(OrderReportService);

  /** Optional event filter; '' (default) means all events / the daily window. */
  readonly eventId = input<string>('');

  readonly rows = signal<SalesIntervalRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly tab = signal<Tab>('summary');
  readonly summary = signal<SalesSummary>({
    totalSales: 0,
    totalPaid: 0,
    totalProtocol: 0,
    remainingToPay: 0,
    remainingProtocol: 0,
  });

  readonly vw = VW;
  readonly vh = VH;

  readonly totalOrdered = computed(() => this.rows().reduce((s, r) => s + r.amountOrdered, 0));
  readonly totalPaid = computed(() => this.rows().reduce((s, r) => s + r.amountPaid, 0));
  readonly totalProtocol = computed(() => this.rows().reduce((s, r) => s + r.amountProtocol, 0));
  readonly totalOrders = computed(() => this.rows().reduce((s, r) => s + r.orderCount, 0));

  /** Running cumulative totals as connect-the-dots lines: ordered, paid, paid+protocol. */
  readonly amountChart = computed<ChartModel>(() => {
    const rows = this.rows();
    const cumOrdered = this.cumulative(rows.map((r) => r.amountOrdered));
    const cumPaid = this.cumulative(rows.map((r) => r.amountPaid));
    const cumProtocol = this.cumulative(rows.map((r) => r.amountProtocol));
    const cumPaidPlusProtocol = cumPaid.map((v, i) => v + cumProtocol[i]);
    return this.buildChart(
      [
        { name: 'Ordered', color: '#3454d1', values: cumOrdered },
        { name: 'Paid + Protocol', color: '#e49e3d', values: cumPaidPlusProtocol },
        { name: 'Paid', color: '#25b865', values: cumPaid },
      ],
      'line',
      false,
      (v) => `${this.fmt(v)} RON`,
    );
  });

  /** Number of orders per interval as rounded bars (integer scale). */
  readonly ordersChart = computed<ChartModel>(() =>
    this.buildChart(
      [{ name: 'Orders', color: '#e49e3d', values: this.rows().map((r) => r.orderCount) }],
      'bar',
      true,
      (v) => `${this.fmt(v)} orders`,
    ),
  );

  constructor() {
    // Reload whenever the selected event changes (runs once on init too).
    effect(() => this.load(this.eventId() || null));
  }

  private load(eventId: string | null): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.sales(eventId).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load sales data.');
        this.loading.set(false);
      },
    });
    this.service.salesSummary(eventId).subscribe({
      next: (s) => this.summary.set(s),
      error: () => {
        /* charts still work; summary stays zeroed */
      },
    });
  }

  setTab(tab: Tab): void {
    this.tab.set(tab);
  }

  private buildChart(
    defs: { name: string; color: string; values: number[] }[],
    kind: 'line' | 'bar',
    integer: boolean,
    tooltip: (v: number) => string,
  ): ChartModel {
    const rows = this.rows();
    const n = rows.length;
    const k = defs.length;
    const plotLeft = PAD_L;
    const plotRight = VW - PAD_R;
    const plotTop = PAD_T;
    const plotBottom = VH - PAD_B;
    const plotW = plotRight - plotLeft;
    const plotH = plotBottom - plotTop;

    const rawMax = Math.max(0, ...defs.flatMap((d) => d.values));
    const step = this.niceStep(rawMax, integer);
    const max = Math.max(step, Math.ceil(rawMax / step) * step);
    const yOf = (v: number) => plotBottom - (max > 0 ? (v / max) * plotH : 0);

    const slotW = n > 0 ? plotW / n : plotW;
    const groupW = slotW * 0.62;
    const barW = groupW / k;
    const innerPad = barW * 0.16;
    const drawW = Math.max(1, barW - innerPad);
    const xLine = (i: number) => (n > 1 ? plotLeft + (i / (n - 1)) * plotW : plotLeft + plotW / 2);

    const series: ChartSeries[] = defs.map((d, j) => {
      let line = '';
      const bars: Bar[] = [];
      if (kind === 'line') {
        line = this.straightLine(d.values.map((v, i) => ({ x: xLine(i), y: yOf(v) })));
      } else {
        d.values.forEach((v, i) => {
          const x = plotLeft + i * slotW + (slotW - groupW) / 2 + j * barW + innerPad / 2;
          const yTop = yOf(v);
          bars.push({
            d: this.roundedTopBar(x, yTop, drawW, plotBottom - yTop, Math.min(3, drawW / 2)),
            title: `${this.timeLabel(rows[i].interval)} · ${d.name} ${tooltip(v)}`,
          });
        });
      }
      return { name: d.name, color: d.color, line, bars };
    });

    const grid: GridLine[] = [];
    for (let value = 0; value <= max + step / 2; value += step) {
      grid.push({ y: yOf(value), label: this.fmt(value) });
    }

    const every = Math.max(1, Math.ceil(n / 12));
    const xticks: XTick[] = [];
    rows.forEach((r, i) => {
      if (i % every === 0) {
        const x = kind === 'line' ? xLine(i) : plotLeft + i * slotW + slotW / 2;
        xticks.push({ x, label: this.timeLabel(r.interval) });
      }
    });

    return { series, grid, xticks, plotLeft, plotRight, plotBottom };
  }

  private straightLine(pts: { x: number; y: number }[]): string {
    if (pts.length === 0) return '';
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  }

  private cumulative(values: number[]): number[] {
    let acc = 0;
    return values.map((v) => (acc += v));
  }

  private roundedTopBar(x: number, y: number, w: number, h: number, r: number): string {
    if (h <= 0) return '';
    const rad = Math.max(0, Math.min(r, w / 2, h));
    const bottom = y + h;
    return (
      `M ${x} ${bottom} L ${x} ${y + rad} Q ${x} ${y} ${x + rad} ${y}` +
      ` L ${x + w - rad} ${y} Q ${x + w} ${y} ${x + w} ${y + rad} L ${x + w} ${bottom} Z`
    );
  }

  private niceStep(rawMax: number, integer: boolean): number {
    if (rawMax <= 0) return 1;
    const rough = rawMax / 4;
    const pow = Math.pow(10, Math.floor(Math.log10(rough)));
    const candidates = integer ? [1, 2, 5, 10] : [1, 2, 2.5, 5, 10];
    let step = 10 * pow;
    for (const m of candidates) {
      if (m * pow >= rough) {
        step = m * pow;
        break;
      }
    }
    return integer ? Math.max(1, Math.round(step)) : step;
  }

  private timeLabel(interval: string): string {
    return interval.length >= 16 ? interval.substring(11, 16) : interval;
  }

  private fmt(v: number): string {
    return Number.isInteger(v)
      ? v.toLocaleString('en-US')
      : v.toLocaleString('en-US', { maximumFractionDigits: 2 });
  }
}
