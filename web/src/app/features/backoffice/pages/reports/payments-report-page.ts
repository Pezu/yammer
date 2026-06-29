import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ComboOption, FilterCombo } from './filter-combo';
import { EventOption, OrderReportService, PaymentReportRow } from './order.service';
import { RoDatePipe } from '../../../../shared/tz';

const PAGE_SIZE = 50;

/** Reports → Payments: the event's payments (server-side paged + filtered) with their type. */
@Component({
  selector: 'app-payments-report-page',
  imports: [DecimalPipe, FilterCombo, RoDatePipe],
  template: `
    <header class="page-header">
      <h1>Payments</h1>
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
    </header>

    <section class="page-body">
      <div class="widget">
        <div class="tab-body">
          @if (!eventId()) {
            <p class="state">Select an event to see payments.</p>
          } @else {
            <div class="table-wrap">
            <table class="grid">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Type</th>
                  <th>Table</th>
                  <th>User</th>
                  <th class="num">Amount</th>
                  <th class="num">Tip</th>
                  <th>Fiscal</th>
                </tr>
                <tr class="filter-row">
                  <th></th>
                  <th><app-filter-combo [value]="fMethod()" [options]="methodOptions()" (changed)="setMethod($event)" /></th>
                  <th><app-filter-combo [value]="fOrderPointId()" [options]="orderPointOptions()" [searchable]="true" (changed)="setOrderPoint($event)" /></th>
                  <th><app-filter-combo [value]="fUser()" [options]="userOptions()" [searchable]="true" (changed)="setUser($event)" /></th>
                  <th class="num"></th>
                  <th class="num"></th>
                  <th><app-filter-combo [value]="fFiscal()" [options]="fiscalOptions()" (changed)="setFiscal($event)" /></th>
                </tr>
              </thead>
              <tbody>
                @for (p of rows(); track p.id) {
                  <tr>
                    <td>{{ p.createdAt | roDate: 'datetime' }}</td>
                    <td>{{ p.method }}</td>
                    <td>{{ p.orderPoint }}</td>
                    <td>{{ p.createdBy }}</td>
                    <td class="num">{{ p.amount | number: '1.2-2' }}</td>
                    <td class="num">{{ p.tip | number: '1.2-2' }}</td>
                    <td>
                      <span class="fiscal" [class.failed]="p.fiscalStatus === 'FAILED'">{{ p.fiscalStatus }}</span>
                      @if (p.fiscalStatus === 'FAILED') {
                        <button type="button" class="retry-btn" [class.spinning]="retrying().has(p.id)" [disabled]="retrying().has(p.id)" (click)="retry(p.id)" title="Retry fiscalization" aria-label="Retry fiscalization">
                          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>
                        </button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="7">
                      <p class="state">{{ loading() ? 'Loading…' : 'No payments.' }}</p>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            </div>

            <div class="pager">
              <span class="range">{{ range() }}</span>
              <div class="pager-btns">
                <button type="button" (click)="prev()" [disabled]="page() <= 1">‹</button>
                <span class="page">{{ page() }} / {{ pages() }}</span>
                <button type="button" (click)="next()" [disabled]="page() >= pages()">›</button>
              </div>
            </div>
          }
        </div>
      </div>
    </section>
  `,
  styleUrl: './payments-report-page.scss',
})
export class PaymentsReportPage {
  private readonly service = inject(OrderReportService);

  // --- event filter ---
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

  // --- column filters ('' = all), applied server-side ---
  readonly fMethod = signal('');
  readonly fOrderPointId = signal('');
  readonly fUser = signal('');
  readonly fFiscal = signal('');
  readonly methodOptions = signal<ComboOption[]>([]);
  readonly orderPointOptions = signal<ComboOption[]>([]);
  readonly userOptions = signal<ComboOption[]>([]);
  readonly fiscalOptions = signal<ComboOption[]>([]);

  // --- current page (server-side) ---
  readonly rows = signal<PaymentReportRow[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly retrying = signal<Set<string>>(new Set());
  readonly page = signal(1);
  readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / PAGE_SIZE)));
  readonly range = computed(() => {
    const total = this.total();
    if (total === 0) return '0';
    const start = (this.page() - 1) * PAGE_SIZE + 1;
    return `${start}–${Math.min(total, this.page() * PAGE_SIZE)} of ${total}`;
  });

  constructor() {
    this.service.listEvents().subscribe({
      next: (events) => {
        this.events.set(events);
        if (events.length === 1) {
          this.selectEvent(events[0].id); // a single event is selected automatically
        }
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
    this.fMethod.set('');
    this.fOrderPointId.set('');
    this.fUser.set('');
    this.fFiscal.set('');
    this.page.set(1);
    this.loadOptions();
    this.load();
  }

  setMethod(v: string): void {
    this.fMethod.set(v);
    this.page.set(1);
    this.load();
  }
  setOrderPoint(v: string): void {
    this.fOrderPointId.set(v);
    this.page.set(1);
    this.load();
  }
  setUser(v: string): void {
    this.fUser.set(v);
    this.page.set(1);
    this.load();
  }
  setFiscal(v: string): void {
    this.fFiscal.set(v);
    this.page.set(1);
    this.load();
  }

  prev(): void {
    if (this.page() <= 1) return;
    this.page.update((p) => p - 1);
    this.load();
  }
  next(): void {
    if (this.page() >= this.pages()) return;
    this.page.update((p) => p + 1);
    this.load();
  }

  /** Re-queue fiscalization for a failed payment; reload the page after the bridge round-trip. */
  retry(paymentId: string): void {
    if (this.retrying().has(paymentId)) return;
    this.retrying.update((s) => new Set(s).add(paymentId));
    this.service.retryFiscal(paymentId).subscribe({
      next: () => setTimeout(() => this.clearRetry(paymentId, true), 2500),
      error: () => this.clearRetry(paymentId, false),
    });
  }
  private clearRetry(paymentId: string, reload: boolean): void {
    this.retrying.update((s) => {
      const next = new Set(s);
      next.delete(paymentId);
      return next;
    });
    if (reload) this.load();
  }

  private loadOptions(): void {
    this.service.paymentFilterOptions(this.eventId()).subscribe({
      next: (o) => {
        this.methodOptions.set(o.methods.map((m) => ({ value: m, label: m })));
        this.userOptions.set(o.users.map((u) => ({ value: u, label: u })));
        this.fiscalOptions.set(o.fiscalStatuses.map((f) => ({ value: f, label: f })));
        this.orderPointOptions.set(o.orderPoints.map((op) => ({ value: op.id, label: op.name })));
      },
      error: () => {
        this.methodOptions.set([]);
        this.userOptions.set([]);
        this.fiscalOptions.set([]);
        this.orderPointOptions.set([]);
      },
    });
  }

  private load(): void {
    if (!this.eventId()) {
      this.rows.set([]);
      this.total.set(0);
      return;
    }
    this.loading.set(true);
    this.service
      .paymentsPaged(this.page() - 1, PAGE_SIZE, {
        eventId: this.eventId(),
        method: this.fMethod() || null,
        orderPointId: this.fOrderPointId() || null,
        createdBy: this.fUser() || null,
        fiscalStatus: this.fFiscal() || null,
      })
      .subscribe({
        next: (res) => {
          this.rows.set(res.content);
          this.total.set(res.total);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
