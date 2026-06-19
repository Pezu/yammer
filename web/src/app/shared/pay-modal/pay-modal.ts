import { Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export type PayMethod = 'CASH' | 'CARD' | 'PROTOCOL';
type TipMode = 'none' | 'p10' | 'p12' | 'p15' | 'customPct' | 'customAmt';

const round2 = (n: number): number => Math.round(n * 100) / 100;

/**
 * The full-payment modal (amount + tip options + method buttons), shared by the table "Pay all"
 * flow and the immediate (non-pay-later) order flow. Presentational: the parent supplies the base
 * {@link amount} and accepted {@link methods}, and handles {@link confirmed} ({method, tip}).
 */
@Component({
  selector: 'app-pay-modal',
  imports: [DecimalPipe],
  templateUrl: './pay-modal.html',
  styleUrl: './pay-modal.scss',
})
export class PayModal {
  readonly title = input('Payment');
  /** Base amount to pay (tip is added on top). */
  readonly amount = input(0);
  readonly protocol = input(false);
  /** Accepted non-protocol methods (CASH/CARD); empty = both. */
  readonly methods = input<string[]>([]);
  readonly submitting = input(false);

  readonly confirmed = output<{ method: PayMethod; tip: number }>();
  readonly cancelled = output<void>();

  readonly tipMode = signal<TipMode>('none');
  readonly tipCustomPercent = signal<number | null>(null);
  readonly tipCustomAmount = signal<number | null>(null);

  readonly computedTip = computed(() => {
    const base = this.amount() || 0;
    let tip = 0;
    switch (this.tipMode()) {
      case 'p10':
        tip = base * 0.1;
        break;
      case 'p12':
        tip = base * 0.12;
        break;
      case 'p15':
        tip = base * 0.15;
        break;
      case 'customPct': {
        const p = this.tipCustomPercent();
        if (p != null && !isNaN(p)) {
          tip = base * (p / 100);
        }
        break;
      }
      case 'customAmt': {
        const a = this.tipCustomAmount();
        if (a != null && !isNaN(a)) {
          tip = a;
        }
        break;
      }
    }
    return Math.max(0, round2(tip));
  });
  readonly totalToPay = computed(() => round2(this.amount() + this.computedTip()));

  readonly payButtons = computed<PayMethod[]>(() => {
    if (this.protocol()) {
      return ['PROTOCOL'];
    }
    const m = (this.methods() ?? []) as PayMethod[];
    return m.length ? m : ['CASH', 'CARD'];
  });

  setTip(mode: TipMode): void {
    this.tipMode.set(mode);
  }

  close(): void {
    this.cancelled.emit();
  }

  pay(method: PayMethod): void {
    this.confirmed.emit({ method, tip: method === 'PROTOCOL' ? 0 : this.computedTip() });
  }
}
