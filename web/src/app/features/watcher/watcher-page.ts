import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DashboardPage } from '../backoffice/pages/reports/dashboard-page';

/** Watcher landing: the same widgets as the backoffice dashboard, plus a logout control. */
@Component({
  selector: 'app-watcher-page',
  imports: [DashboardPage],
  template: `
    <button type="button" class="watcher-logout" (click)="logout()" title="Logout" aria-label="Logout">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
    </button>
    <app-dashboard-page class="watcher" />
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .watcher-logout {
        position: fixed;
        top: 0.85rem;
        right: 0.7rem;
        z-index: 50;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 34px;
        height: 34px;
        color: var(--danger);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 8px;
        cursor: pointer;
      }
      .watcher-logout:hover {
        background: var(--page-bg);
      }
      /* keep the dashboard's event dropdown clear of the floating logout button */
      :host ::ng-deep app-dashboard-page.watcher .page-header {
        padding-right: 3.4rem;
      }
    `,
  ],
})
export class WatcherPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
