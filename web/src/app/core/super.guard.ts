import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Allows the route only for SUPER users; others are redirected to the backoffice home. */
export const superGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isSuper() ? true : router.parseUrl('/backoffice');
};
