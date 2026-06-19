import { Component, computed, input, output, signal } from '@angular/core';

export interface ComboOption {
  value: string;
  label: string;
}

/**
 * Small custom dropdown (no native OS select) for the orders-report column filters. Shows an
 * always-present "all" option plus the given options, with optional type-ahead search.
 */
@Component({
  selector: 'app-filter-combo',
  template: `
    <div class="fc">
      <button type="button" class="fc-trigger" [class.placeholder]="!value()" (click)="toggle()">
        <span class="fc-label">{{ label() }}</span>
        <svg class="caret" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>
      </button>
      @if (open()) {
        <div class="fc-backdrop" (click)="close()"></div>
        <div class="fc-menu">
          @if (searchable()) {
            <div class="fc-search">
              <input #s type="text" placeholder="Search…" [value]="search()" (input)="search.set(s.value)" (keyup.escape)="close()" autofocus />
            </div>
          }
          <ul class="fc-list">
            <li>
              <button type="button" class="fc-opt" [class.active]="!value()" (click)="pick('')">{{ allLabel() }}</button>
            </li>
            @for (o of filtered(); track o.value) {
              <li>
                <button type="button" class="fc-opt" [class.active]="o.value === value()" (click)="pick(o.value)">{{ o.label }}</button>
              </li>
            } @empty {
              <li class="fc-empty">No matches</li>
            }
          </ul>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .fc {
        position: relative;
      }
      .fc-trigger {
        display: inline-flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.25rem;
        width: 100%;
        padding: 3px 5px;
        font: inherit;
        font-size: 0.72rem;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 4px;
        cursor: pointer;
      }
      .fc-trigger.placeholder {
        color: var(--muted);
      }
      .fc-label {
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
      }
      .fc-trigger .caret {
        flex: none;
        color: var(--muted);
      }
      .fc-backdrop {
        position: fixed;
        inset: 0;
        z-index: 30;
      }
      .fc-menu {
        position: absolute;
        top: calc(100% + 3px);
        left: 0;
        z-index: 31;
        min-width: 100%;
        max-width: 220px;
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 6px;
        box-shadow: 0 0.5rem 1.5rem rgba(18, 27, 46, 0.15);
        overflow: hidden;
      }
      .fc-search {
        padding: 0.4rem;
        border-bottom: 1px solid var(--border);
      }
      .fc-search input {
        width: 100%;
        padding: 0.3rem 0.45rem;
        font: inherit;
        font-size: 0.75rem;
        border: 1px solid var(--border);
        border-radius: 4px;
      }
      .fc-search input:focus {
        outline: none;
        border-color: var(--primary);
      }
      .fc-list {
        margin: 0;
        padding: 0.2rem;
        max-height: 220px;
        overflow-y: auto;
        list-style: none;
      }
      .fc-opt {
        display: block;
        width: 100%;
        padding: 0.35rem 0.5rem;
        font: inherit;
        font-size: 0.75rem;
        text-align: left;
        color: var(--text);
        background: none;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        white-space: nowrap;
      }
      .fc-opt:hover {
        background: var(--page-bg);
      }
      .fc-opt.active {
        color: var(--primary);
        background: rgba(52, 84, 209, 0.08);
        font-weight: 600;
      }
      .fc-empty {
        padding: 0.4rem 0.5rem;
        font-size: 0.75rem;
        color: var(--muted);
      }
    `,
  ],
})
export class FilterCombo {
  readonly value = input<string>('');
  readonly options = input<ComboOption[]>([]);
  readonly allLabel = input<string>('All');
  readonly searchable = input<boolean>(false);
  readonly changed = output<string>();

  readonly open = signal(false);
  readonly search = signal('');

  readonly label = computed(() => {
    const v = this.value();
    if (!v) return this.allLabel();
    return this.options().find((o) => o.value === v)?.label ?? v;
  });

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const all = this.options();
    return q ? all.filter((o) => o.label.toLowerCase().includes(q)) : all;
  });

  toggle(): void {
    this.open.update((o) => !o);
    if (this.open()) this.search.set('');
  }
  close(): void {
    this.open.set(false);
  }
  pick(v: string): void {
    this.open.set(false);
    this.changed.emit(v);
  }
}
