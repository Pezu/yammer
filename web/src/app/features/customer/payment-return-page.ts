import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CustomerOrderPointService } from './customer-order-point.service';

type View = 'checking' | 'paid' | 'failed';

/**
 * Landing page after returning from the Netopia gateway (pay-now flow). The browser return is NOT
 * authoritative — payment is confirmed server-side by the IPN — so this page polls the payment
 * status for a short while and shows the outcome, then offers a link back to the menu.
 */
@Component({
  selector: 'app-payment-return-page',
  imports: [],
  template: `
    <main class="ret">
      @switch (view()) {
        @case ('checking') {
          <div class="spinner" aria-hidden="true"></div>
          <h1>Confirming your payment…</h1>
          <p class="sub">This only takes a moment.</p>
        }
        @case ('paid') {
          <div class="icon ok" aria-hidden="true">✓</div>
          <h1>Payment received</h1>
          <p class="sub">Thank you! Your order has been sent.</p>
        }
        @case ('failed') {
          <div class="icon bad" aria-hidden="true">!</div>
          <h1>Payment not completed</h1>
          <p class="sub">You were not charged. Please try ordering again.</p>
        }
      }
      <button type="button" class="back" (click)="backToMenu()">Back to menu</button>
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: #fff; }
    .ret {
      max-width: 30rem;
      margin: 0 auto;
      padding: 5rem 1.5rem;
      text-align: center;
    }
    h1 { margin: 1.25rem 0 0.25rem; font-size: 1.4rem; color: var(--text); }
    .sub { margin: 0; color: var(--muted); }
    .icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      border-radius: 50%;
      font-size: 32px;
      font-weight: 700;
      color: #fff;
    }
    .icon.ok { background: #1f9d55; }
    .icon.bad { background: var(--danger); }
    .spinner {
      width: 44px;
      height: 44px;
      margin: 0 auto;
      border: 4px solid var(--border);
      border-top-color: var(--primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .back {
      margin-top: 2rem;
      padding: 0.7rem 1.5rem;
      font: inherit;
      font-weight: 700;
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--primary);
      border-radius: 8px;
      cursor: pointer;
    }
  `,
})
export class PaymentReturnPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(CustomerOrderPointService);

  private readonly opId = this.route.snapshot.paramMap.get('opId') ?? '';
  private readonly reference = this.route.snapshot.queryParamMap.get('ref');

  readonly view = signal<View>('checking');

  private timer: ReturnType<typeof setTimeout> | null = null;
  private attempts = 0;
  private static readonly MAX_ATTEMPTS = 15; // ~30s at 2s intervals

  ngOnInit(): void {
    document.body.style.background = '#fff';
    if (!this.reference) {
      this.view.set('failed');
      return;
    }
    this.poll();
  }

  ngOnDestroy(): void {
    document.body.style.background = '';
    if (this.timer) clearTimeout(this.timer);
  }

  private poll(): void {
    if (!this.reference) return;
    this.service.paymentStatus(this.reference).subscribe({
      next: (s) => {
        if (s.status === 'PAID') {
          this.view.set('paid');
        } else if (s.status === 'FAILED' || s.status === 'EXPIRED') {
          this.view.set('failed');
        } else if (++this.attempts >= PaymentReturnPage.MAX_ATTEMPTS) {
          // Still pending after the window — leave a gentle outcome; the order will land once the
          // gateway's IPN arrives. Treat as "checking timed out" → failed-style message.
          this.view.set('failed');
        } else {
          this.timer = setTimeout(() => this.poll(), 2000);
        }
      },
      error: () => {
        if (++this.attempts >= PaymentReturnPage.MAX_ATTEMPTS) {
          this.view.set('failed');
        } else {
          this.timer = setTimeout(() => this.poll(), 2000);
        }
      },
    });
  }

  backToMenu(): void {
    this.router.navigate(['/customer/order-point', this.opId]);
  }
}
