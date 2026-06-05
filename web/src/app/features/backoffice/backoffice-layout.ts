import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AppLogo } from '../../shared/logo.component';

@Component({
  selector: 'app-backoffice-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, AppLogo],
  templateUrl: './backoffice-layout.html',
  styleUrl: './backoffice-layout.scss',
})
export class BackofficeLayout {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Clients management is SUPER-only. */
  readonly isSuper = this.auth.isSuper;

  readonly username = computed(() => this.auth.session()?.username ?? '');
  readonly roles = computed(() => this.auth.session()?.roles ?? []);
  readonly initials = computed(() => this.username().slice(0, 2).toUpperCase() || '?');

  /** Collapsible "Configuration" group — open by default. */
  readonly configOpen = signal(true);
  /** Collapsible "Catalog" group — open by default. */
  readonly catalogOpen = signal(true);
  /** Collapsible "Reports" group — open by default. */
  readonly reportsOpen = signal(true);
  /** User dropdown in the top bar. */
  readonly userMenuOpen = signal(false);

  toggleConfig(): void {
    this.configOpen.update((open) => !open);
  }

  toggleCatalog(): void {
    this.catalogOpen.update((open) => !open);
  }

  toggleReports(): void {
    this.reportsOpen.update((open) => !open);
  }

  toggleUserMenu(): void {
    this.userMenuOpen.update((open) => !open);
  }

  closeUserMenu(): void {
    this.userMenuOpen.set(false);
  }

  logout(): void {
    this.userMenuOpen.set(false);
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
