import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { OrderReportService, SalesIntervalRow } from './order.service';

interface Bar {
  d: string;
  title: string;
}
interface Dot {
  cx: number;
  cy: number;
  title: string;
}
interface ChartSeries {
  name: string;
  color: string;
  gradId: string;
  line: string;
  area: string;
  dots: Dot[];
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

const VW = 820;
const VH = 300;
const PAD_L = 52;
const PAD_R = 14;
const PAD_T = 14;
const PAD_B = 30;

@Component({
  selector: 'app-sales-report-page',
  imports: [DecimalPipe],
  templateUrl: './sales-report-page.html',
  styleUrl: './sales-report-page.scss',
})
export class SalesReportPage {
  private readonly service = inject(OrderReportService);

  readonly rows = signal<SalesIntervalRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

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
        // draw order matters: Paid+Protocol first, then Paid on top, so all three
        // lines stay visible where Paid and Paid+Protocol coincide (no protocol).
        { name: 'Ordered', color: '#3454d1', gradId: 'g-ordered', values: cumOrdered },
        { name: 'Paid + Protocol', color: '#e49e3d', gradId: 'g-protocol', values: cumPaidPlusProtocol },
        { name: 'Paid', color: '#25b865', gradId: 'g-paid', values: cumPaid },
      ],
      'line',
      false,
      (v) => `${this.fmt(v)} RON`,
    );
  });

  /** Number of orders per interval as rounded bars (integer scale). */
  readonly ordersChart = computed<ChartModel>(() =>
    this.buildChart(
      [{ name: 'Orders', color: '#e49e3d', gradId: 'g-orders', values: this.rows().map((r) => r.orderCount) }],
      'bar',
      true,
      (v) => `${this.fmt(v)} orders`,
    ),
  );

  constructor() {
    this.service.sales().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load sales data.');
        this.loading.set(false);
      },
    });
  }

  private buildChart(
    defs: { name: string; color: string; gradId: string; values: number[] }[],
    kind: 'area' | 'line' | 'bar',
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

    // bar geometry (only used when kind === 'bar')
    const slotW = n > 0 ? plotW / n : plotW;
    const groupW = slotW * 0.62;
    const barW = groupW / k;
    const innerPad = barW * 0.16;
    const drawW = Math.max(1, barW - innerPad);

    const xLine = (i: number) => (n > 1 ? plotLeft + (i / (n - 1)) * plotW : plotLeft + plotW / 2);

    const series: ChartSeries[] = defs.map((d, j) => {
      let line = '';
      let area = '';
      const dots: Dot[] = [];
      const bars: Bar[] = [];

      if (kind === 'area' || kind === 'line') {
        const pts = d.values.map((v, i) => ({ x: xLine(i), y: yOf(v) }));
        line = kind === 'area' ? this.smoothLine(pts, plotTop, plotBottom) : this.straightLine(pts);
        area =
          kind === 'area' && pts.length > 0
            ? `${line} L ${pts[pts.length - 1].x} ${plotBottom} L ${pts[0].x} ${plotBottom} Z`
            : '';
        d.values.forEach((v, i) =>
          dots.push({ cx: xLine(i), cy: yOf(v), title: `${this.timeLabel(rows[i].interval)} · ${d.name} ${tooltip(v)}` }),
        );
      } else {
        d.values.forEach((v, i) => {
          const x = plotLeft + i * slotW + (slotW - groupW) / 2 + j * barW + innerPad / 2;
          const yTop = yOf(v);
          const h = plotBottom - yTop;
          bars.push({
            d: this.roundedTopBar(x, yTop, drawW, h, Math.min(3, drawW / 2)),
            title: `${this.timeLabel(rows[i].interval)} · ${d.name} ${tooltip(v)}`,
          });
        });
      }

      return { name: d.name, color: d.color, gradId: d.gradId, line, area, dots, bars };
    });

    const grid: GridLine[] = [];
    for (let value = 0; value <= max + step / 2; value += step) {
      grid.push({ y: yOf(value), label: this.fmt(value) });
    }

    const every = Math.max(1, Math.ceil(n / 12));
    const xticks: XTick[] = [];
    rows.forEach((r, i) => {
      if (i % every === 0) {
        const x = kind === 'area' ? xLine(i) : plotLeft + i * slotW + slotW / 2;
        xticks.push({ x, label: this.timeLabel(r.interval) });
      }
    });

    return { series, grid, xticks, plotLeft, plotRight, plotBottom };
  }

  /**
   * Catmull-Rom → cubic-bezier smoothing. Control points are clamped to the plot
   * band [yMin, yMax]; since a cubic Bézier stays within its control points' convex
   * hull, this prevents the curve from dipping below the baseline (no "negative" bows).
   */
  private smoothLine(pts: { x: number; y: number }[], yMin: number, yMax: number): string {
    if (pts.length === 0) return '';
    if (pts.length === 1) return `M ${pts[0].x} ${pts[0].y} L ${pts[0].x} ${pts[0].y}`;
    const clampY = (y: number) => Math.max(yMin, Math.min(yMax, y));
    let d = `M ${pts[0].x} ${pts[0].y}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i - 1] ?? pts[i];
      const p1 = pts[i];
      const p2 = pts[i + 1];
      const p3 = pts[i + 2] ?? p2;
      const cp1x = p1.x + (p2.x - p0.x) / 6;
      const cp1y = clampY(p1.y + (p2.y - p0.y) / 6);
      const cp2x = p2.x - (p3.x - p1.x) / 6;
      const cp2y = clampY(p2.y - (p3.y - p1.y) / 6);
      d += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`;
    }
    return d;
  }

  /** Straight connect-the-dots polyline. */
  private straightLine(pts: { x: number; y: number }[]): string {
    if (pts.length === 0) return '';
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  }

  /** Running cumulative sum of a series. */
  private cumulative(values: number[]): number[] {
    let acc = 0;
    return values.map((v) => (acc += v));
  }

  /** Column with rounded top corners; bottom sits flat on the baseline. */
  private roundedTopBar(x: number, y: number, w: number, h: number, r: number): string {
    if (h <= 0) return '';
    const rad = Math.max(0, Math.min(r, w / 2, h));
    const bottom = y + h;
    return (
      `M ${x} ${bottom}` +
      ` L ${x} ${y + rad}` +
      ` Q ${x} ${y} ${x + rad} ${y}` +
      ` L ${x + w - rad} ${y}` +
      ` Q ${x + w} ${y} ${x + w} ${y + rad}` +
      ` L ${x + w} ${bottom} Z`
    );
  }

  /** A "nice" axis step (~4 ticks). For integer scales the step is a whole number ≥ 1. */
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
