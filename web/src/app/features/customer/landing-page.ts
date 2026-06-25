import { Component, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';

/**
 * Public root (`/`): asks the API for the first non-pay-later order point of the active event and
 * redirects the customer straight to its ordering page. Falls back to /login if none is available.
 */
@Component({
  selector: 'app-landing-page',
  template: `<p class="state">Se încarcă…</p>`,
  styles: [
    `
      .state {
        padding: 3rem 1rem;
        text-align: center;
        color: var(--muted);
      }
    `,
  ],
})
export class LandingPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  constructor() {
    this.http
      .get<{ orderPointId: string }>(`${environment.apiUrl}/public/landing`)
      .subscribe({
        next: (r) =>
          this.router.navigate(['/customer/order-point', r.orderPointId], { replaceUrl: true }),
        error: () => this.router.navigateByUrl('/login', { replaceUrl: true }),
      });
  }
}
