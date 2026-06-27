import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { EventOption, FinalReportRow, OrderReportService } from './order.service';

interface FinalRow {
  table: string;
  paidCard: number;
  paidCash: number;
  tipCard: number;
  tipCash: number;
  total: number;
}
interface FinalGroup {
  user: string;
  rows: FinalRow[];
  subtotal: FinalRow;
}

/** Reports → Final: per user + table, card/cash paid and tip; matches the dashboard "paid" totals. */
@Component({
  selector: 'app-final-report-page',
  imports: [DecimalPipe],
  template: `
    <header class="page-header">
      <h1>Final</h1>
      <div class="header-actions">
        <button type="button" class="export-btn" [disabled]="!groups().length" (click)="exportExcel()">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
          Export to Excel
        </button>
        <div class="event-combo">
          <button type="button" class="event-trigger" [class.placeholder]="!eventId()" (click)="toggleEventCombo()" aria-label="Filter by event">
            <span>{{ eventName() }}</span>
            <svg class="caret" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>
          </button>
          @if (eventComboOpen()) {
            <div class="event-backdrop" (click)="closeEventCombo()"></div>
            <div class="event-menu">
              <div class="event-search">
                <input type="text" #eq placeholder="Search events…" [value]="eventSearch()" (input)="eventSearch.set(eq.value)" (keyup.escape)="closeEventCombo()" autofocus />
              </div>
              <ul class="event-list">
                @for (e of eventOptions(); track e.id) {
                  <li><button type="button" class="event-option" [class.active]="e.id === eventId()" (click)="selectEvent(e.id)">{{ e.name }}</button></li>
                } @empty {
                  <li class="event-empty">No events found</li>
                }
              </ul>
            </div>
          }
        </div>
      </div>
    </header>

    <section class="page-body">
      <div class="widget">
        <div class="tab-body">
          @if (!eventId()) {
            <p class="state">Select an event to see the final report.</p>
          } @else {
            <div class="table-wrap">
              <table class="grid">
                <thead>
                  <tr>
                    <th>User</th>
                    <th>Table</th>
                    <th class="num">Paid card</th>
                    <th class="num">Paid cash</th>
                    <th class="num">Tip card</th>
                    <th class="num">Tip cash</th>
                    <th class="num">Total</th>
                  </tr>
                </thead>
                <tbody>
                  @for (g of groups(); track g.user) {
                    @for (r of g.rows; track r.table) {
                      <tr>
                        <td>{{ r === g.rows[0] ? g.user : '' }}</td>
                        <td>{{ r.table }}</td>
                        <td class="num">{{ r.paidCard | number: '1.2-2' }}</td>
                        <td class="num">{{ r.paidCash | number: '1.2-2' }}</td>
                        <td class="num">{{ r.tipCard | number: '1.2-2' }}</td>
                        <td class="num">{{ r.tipCash | number: '1.2-2' }}</td>
                        <td class="num">{{ r.total | number: '1.2-2' }}</td>
                      </tr>
                    }
                    <tr class="subtotal-row">
                      <td>{{ g.user }}</td>
                      <td>Total</td>
                      <td class="num">{{ g.subtotal.paidCard | number: '1.2-2' }}</td>
                      <td class="num">{{ g.subtotal.paidCash | number: '1.2-2' }}</td>
                      <td class="num">{{ g.subtotal.tipCard | number: '1.2-2' }}</td>
                      <td class="num">{{ g.subtotal.tipCash | number: '1.2-2' }}</td>
                      <td class="num">{{ g.subtotal.total | number: '1.2-2' }}</td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="7"><p class="state">{{ loading() ? 'Loading…' : 'No payments.' }}</p></td>
                    </tr>
                  }
                </tbody>
                @if (groups().length) {
                  <tfoot>
                    <tr class="grand-row">
                      <td>ALL USERS</td>
                      <td>Grand total</td>
                      <td class="num">{{ grandTotal().paidCard | number: '1.2-2' }}</td>
                      <td class="num">{{ grandTotal().paidCash | number: '1.2-2' }}</td>
                      <td class="num">{{ grandTotal().tipCard | number: '1.2-2' }}</td>
                      <td class="num">{{ grandTotal().tipCash | number: '1.2-2' }}</td>
                      <td class="num">{{ grandTotal().total | number: '1.2-2' }}</td>
                    </tr>
                  </tfoot>
                }
              </table>
            </div>
          }
        </div>
      </div>
    </section>
  `,
  styleUrls: ['./payments-report-page.scss'],
  styles: [
    `
      .header-actions {
        display: flex;
        align-items: center;
        gap: 0.6rem;
      }
      .export-btn {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.45rem 0.8rem;
        font: inherit;
        font-size: 0.85rem;
        font-weight: 600;
        color: #fff;
        background: var(--primary);
        border: 1px solid var(--primary);
        border-radius: 8px;
        cursor: pointer;
      }
      .export-btn:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .grid tbody .subtotal-row td {
        font-weight: 700;
        background: var(--page-bg);
        border-top: 1px solid var(--border);
      }
      .grid tfoot .grand-row td {
        font-weight: 800;
        background: var(--page-bg);
        border-top: 2px solid var(--primary);
      }
    `,
  ],
})
export class FinalReportPage {
  private readonly service = inject(OrderReportService);

  readonly events = signal<EventOption[]>([]);
  readonly eventId = signal<string>('');
  readonly eventComboOpen = signal(false);
  readonly eventSearch = signal('');
  readonly eventName = computed(
    () => this.events().find((e) => e.id === this.eventId())?.name ?? 'Select an event',
  );
  readonly eventOptions = computed(() => {
    const q = this.eventSearch().trim().toLowerCase();
    const all = this.events();
    return q ? all.filter((e) => e.name.toLowerCase().includes(q)) : all;
  });

  readonly loading = signal(false);
  readonly groups = signal<FinalGroup[]>([]);
  readonly grandTotal = computed(() => this.groups().reduce(
    (acc, g) => this.add(acc, g.subtotal),
    this.zero('Grand total'),
  ));

  constructor() {
    this.service.listEvents().subscribe({
      next: (events) => {
        this.events.set(events);
        if (events.length === 1) this.selectEvent(events[0].id);
      },
    });
  }

  toggleEventCombo(): void {
    this.eventComboOpen.update((o) => !o);
    if (this.eventComboOpen()) this.eventSearch.set('');
  }
  closeEventCombo(): void {
    this.eventComboOpen.set(false);
  }
  selectEvent(id: string): void {
    this.eventComboOpen.set(false);
    if (this.eventId() === id) return;
    this.eventId.set(id);
    this.load();
  }

  private load(): void {
    if (!this.eventId()) {
      this.groups.set([]);
      return;
    }
    this.loading.set(true);
    this.service.finalReport(this.eventId()).subscribe({
      next: (rows) => {
        this.groups.set(this.group(rows));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  /** Group the flat rows by user (backend already orders by user, table) and compute subtotals. */
  private group(rows: FinalReportRow[]): FinalGroup[] {
    const byUser = new Map<string, FinalRow[]>();
    for (const r of rows) {
      const row: FinalRow = {
        table: r.table,
        paidCard: r.paidCard,
        paidCash: r.paidCash,
        tipCard: r.tipCard,
        tipCash: r.tipCash,
        total: r.paidCard + r.paidCash + r.tipCard + r.tipCash,
      };
      const list = byUser.get(r.userName);
      if (list) list.push(row);
      else byUser.set(r.userName, [row]);
    }
    return [...byUser.entries()].map(([user, rs]) => ({
      user,
      rows: rs,
      subtotal: rs.reduce((acc, r) => this.add(acc, r), this.zero('Total')),
    }));
  }

  private zero(table: string): FinalRow {
    return { table, paidCard: 0, paidCash: 0, tipCard: 0, tipCash: 0, total: 0 };
  }
  private add(a: FinalRow, b: FinalRow): FinalRow {
    return {
      table: a.table,
      paidCard: a.paidCard + b.paidCard,
      paidCash: a.paidCash + b.paidCash,
      tipCard: a.tipCard + b.tipCard,
      tipCash: a.tipCash + b.tipCash,
      total: a.total + b.total,
    };
  }

  /** Build an Excel-openable file (HTML table) from the grouped data and download it. */
  exportExcel(): void {
    const esc = (s: string) =>
      s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const num = (v: number) =>
      `<td style="mso-number-format:'0\\.00'">${v.toFixed(2)}</td>`;
    const cells = (user: string, table: string, r: FinalRow) =>
      `<tr><td>${esc(user)}</td><td>${esc(table)}</td>${num(r.paidCard)}${num(r.paidCash)}${num(r.tipCard)}${num(r.tipCash)}${num(r.total)}</tr>`;

    let body = '';
    for (const g of this.groups()) {
      for (const r of g.rows) body += cells(g.user, r.table, r);
      body += `<tr style="font-weight:bold;background:#eee">${cells(g.user, 'Total', g.subtotal).slice(4)}`;
    }
    const gt = this.grandTotal();
    const grand = `<tr style="font-weight:bold;background:#ddd"><td>ALL USERS</td><td>Grand total</td>${num(gt.paidCard)}${num(gt.paidCash)}${num(gt.tipCard)}${num(gt.tipCash)}${num(gt.total)}</tr>`;

    const html =
      `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel">` +
      `<head><meta charset="utf-8"></head><body><table border="1"><thead>` +
      `<tr><th>User</th><th>Table</th><th>Paid card</th><th>Paid cash</th><th>Tip card</th><th>Tip cash</th><th>Total</th></tr>` +
      `</thead><tbody>${body}${grand}</tbody></table></body></html>`;

    const safe = this.eventName().replace(/[^a-z0-9]+/gi, '-').toLowerCase();
    this.download(html, `final-report-${safe}.xls`);
  }

  private download(html: string, filename: string): void {
    const blob = new Blob(['﻿', html], { type: 'application/vnd.ms-excel' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
