import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { AppLogo } from '../../shared/logo.component';
import { WaiterPushService } from './waiter-push.service';

@Component({
  selector: 'app-waiter-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, AppLogo],
  templateUrl: './waiter-layout.html',
  styleUrl: './waiter-layout.scss',
})
export class WaiterLayout implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly toast = inject(ToastService);
  readonly push = inject(WaiterPushService);

  /** Title of the current page, shown centered in the topbar (from each route's `title`). */
  readonly pageTitle = signal('');

  // Disable browser zoom (pinch / double-tap) only while in the waiter app.
  private readonly viewportMeta = document.querySelector('meta[name="viewport"]');
  private readonly previousViewport = this.viewportMeta?.getAttribute('content') ?? '';

  constructor() {
    this.viewportMeta?.setAttribute(
      'content',
      'width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no',
    );
    this.pageTitle.set(this.currentTitle());
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.pageTitle.set(this.currentTitle()));
    this.push.refresh();
  }

  /** Enable/disable OS push notifications for this device (asks permission on enable). */
  async toggleNotifications(): Promise<void> {
    if (this.push.subscribed()) {
      await this.push.disable();
      this.toast.show('Notifications off');
      return;
    }
    const ok = await this.push.enable();
    this.toast.show(ok ? 'Notifications enabled' : 'Could not enable notifications');
  }

  /** The deepest activated child route's `title` (snapshot may be absent mid-activation). */
  private currentTitle(): string {
    let r: ActivatedRoute | null = this.route.firstChild;
    while (r?.firstChild) {
      r = r.firstChild;
    }
    return r?.snapshot?.title ?? '';
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

  async logout(): Promise<void> {
    this.menuOpen.set(false);
    // Tear down the push subscription first — /push/unsubscribe is authenticated, and we must
    // not leave this device receiving the logged-out user's notifications.
    await this.push.disable();
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
