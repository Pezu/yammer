import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { AuthService } from './auth.service';

/** Attaches the JWT to API requests and bounces to /login on 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.token;
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    tap({
      error: (err) => {
        if (err?.status === 401) {
          auth.logout();
          router.navigateByUrl('/login');
        }
      },
    }),
  );
};
