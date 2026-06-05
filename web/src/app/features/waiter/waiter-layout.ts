import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { AppLogo } from '../../shared/logo.component';

@Component({
  selector: 'app-waiter-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, AppLogo],
  templateUrl: './waiter-layout.html',
  styleUrl: './waiter-layout.scss',
})
export class WaiterLayout implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly toast = inject(ToastService);

  // Disable browser zoom (pinch / double-tap) only while in the waiter app.
  private readonly viewportMeta = document.querySelector('meta[name="viewport"]');
  private readonly previousViewport = this.viewportMeta?.getAttribute('content') ?? '';

  constructor() {
    this.viewportMeta?.setAttribute(
      'content',
      'width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no',
    );
  }

  ngOnDestroy(): void {
    this.viewportMeta?.setAttribute('content', this.previousViewport);
  }

  readonly username = computed(() => this.auth.session()?.username ?? '');
  readonly roles = computed(() => this.auth.session()?.roles ?? []);
  readonly initials = computed(() => this.username().slice(0, 2).toUpperCase() || '?');

  readonly menuOpen = signal(false);

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }
  closeMenu(): void {
    this.menuOpen.set(false);
  }

  logout(): void {
    this.menuOpen.set(false);
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
