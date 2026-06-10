import { Component, computed, input, output, signal } from '@angular/core';

interface DayCell {
  day: number;
  iso: string;
  otherMonth: boolean;
  today: boolean;
  selected: boolean;
}

/**
 * Custom date picker (no native OS date input). Value is an ISO date string
 * 'YYYY-MM-DD' (or '' when empty). A trigger shows the formatted date; clicking
 * opens a styled month-grid dropdown.
 */
@Component({
  selector: 'app-date-picker',
  template: `
    <div class="dp" [class.open]="open()">
      <button type="button" class="dp-trigger" [class.placeholder]="!value()" (click)="toggle()">
        <svg class="dp-cal" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
          <line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line>
        </svg>
        <span class="dp-value">{{ value() ? display() : placeholder() }}</span>
        <svg class="dp-caret" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>
      </button>

      @if (open()) {
        <div class="dp-backdrop" (click)="close()"></div>
        <div class="dp-dropdown">
          <div class="dp-head">
            <button type="button" class="dp-nav" (click)="prevMonth()" aria-label="Previous month">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
            </button>
            <span class="dp-monthyear">{{ monthLabel() }}</span>
            <button type="button" class="dp-nav" (click)="nextMonth()" aria-label="Next month">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
            </button>
          </div>
          <div class="dp-weekdays">
            @for (w of weekdays; track w) {
              <span>{{ w }}</span>
            }
          </div>
          <div class="dp-days">
            @for (cell of cells(); track cell.iso) {
              <button
                type="button"
                class="dp-day"
                [class.other-month]="cell.otherMonth"
                [class.today]="cell.today"
                [class.selected]="cell.selected"
                (click)="select(cell)"
              >{{ cell.day }}</button>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    `
    :host { display: block; }
    .dp { position: relative; }
    .dp-trigger {
      display: flex; align-items: center; gap: 0.5rem; width: 100%;
      padding: 0.45rem 0.7rem; font: inherit; font-size: 0.9rem;
      color: var(--text); background: #fff; border: 1px solid var(--border);
      border-radius: 6px; cursor: pointer; text-align: left;
    }
    .dp-trigger:hover { border-color: #cbd5e1; }
    .dp.open .dp-trigger { border-color: var(--primary); box-shadow: 0 0 0 0.2rem rgba(52, 84, 209, 0.15); }
    .dp-trigger.placeholder .dp-value { color: #94a3b8; }
    .dp-cal { color: var(--muted); flex-shrink: 0; }
    .dp-value { flex: 1; }
    .dp-caret { color: var(--muted); flex-shrink: 0; transition: transform 0.15s ease; }
    .dp.open .dp-caret { transform: rotate(180deg); }

    .dp-backdrop { position: fixed; inset: 0; z-index: 50; }
    .dp-dropdown {
      position: absolute; top: calc(100% + 4px); left: 0; z-index: 51;
      background: #fff; border: 1px solid var(--border); border-radius: 8px;
      box-shadow: 0 10px 25px rgba(18, 27, 46, 0.15); padding: 12px; min-width: 280px;
    }
    .dp-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
    .dp-nav {
      width: 28px; height: 28px; border: 1px solid var(--border); background: #fff;
      border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: center;
      color: var(--muted);
    }
    .dp-nav:hover { background: var(--page-bg); color: var(--text); }
    .dp-monthyear { font-size: 0.9rem; font-weight: 600; color: var(--text); }
    .dp-weekdays { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; margin-bottom: 8px; }
    .dp-weekdays span { font-size: 11px; font-weight: 600; color: var(--muted); text-align: center; padding: 4px; text-transform: uppercase; }
    .dp-days { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; }
    .dp-day {
      font: inherit; font-size: 13px; color: var(--text); text-align: center; padding: 8px 4px;
      border: none; background: none; border-radius: 6px; cursor: pointer;
    }
    .dp-day:hover { background: var(--page-bg); }
    .dp-day.other-month { color: #cbd5e1; }
    .dp-day.today { font-weight: 700; color: var(--primary); }
    .dp-day.selected { background: var(--primary); color: #fff; }
    .dp-day.selected:hover { background: var(--primary-hover); }
    `,
  ],
})
export class DatePicker {
  readonly value = input<string>('');
  readonly placeholder = input<string>('Select date');
  readonly valueChange = output<string>();

  readonly open = signal(false);
  // The displayed month as a 'YYYY-MM' anchor (signal holds year*12+month).
  private readonly viewAnchor = signal<number>(this.todayAnchor());

  readonly weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  private readonly months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];
  private readonly shortMonths = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];

  readonly display = computed(() => {
    const v = this.value();
    if (!v) return '';
    const [y, m, d] = v.split('-').map(Number);
    return `${String(d).padStart(2, '0')} ${this.shortMonths[m - 1]} ${y}`;
  });

  readonly monthLabel = computed(() => {
    const a = this.viewAnchor();
    return `${this.months[a % 12]} ${Math.floor(a / 12)}`;
  });

  readonly cells = computed<DayCell[]>(() => {
    const a = this.viewAnchor();
    const year = Math.floor(a / 12);
    const month = a % 12;
    const first = new Date(year, month, 1);
    const startOffset = (first.getDay() + 6) % 7; // Monday = 0
    const start = new Date(year, month, 1 - startOffset);
    const today = this.isoOf(new Date());
    const selected = this.value();
    const result: DayCell[] = [];
    for (let i = 0; i < 42; i++) {
      const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
      const iso = this.isoOf(date);
      result.push({
        day: date.getDate(),
        iso,
        otherMonth: date.getMonth() !== month,
        today: iso === today,
        selected: iso === selected,
      });
    }
    return result;
  });

  toggle(): void {
    if (!this.open()) {
      this.viewAnchor.set(this.anchorFor(this.value()));
    }
    this.open.update((o) => !o);
  }
  close(): void {
    this.open.set(false);
  }

  prevMonth(): void {
    this.viewAnchor.update((a) => a - 1);
  }
  nextMonth(): void {
    this.viewAnchor.update((a) => a + 1);
  }

  select(cell: DayCell): void {
    this.valueChange.emit(cell.iso);
    this.open.set(false);
  }

  private isoOf(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
      date.getDate(),
    ).padStart(2, '0')}`;
  }

  private anchorFor(value: string): number {
    if (value) {
      const [y, m] = value.split('-').map(Number);
      return y * 12 + (m - 1);
    }
    return this.todayAnchor();
  }

  private todayAnchor(): number {
    const now = new Date();
    return now.getFullYear() * 12 + now.getMonth();
  }
}
