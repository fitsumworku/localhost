import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const dialog = inject(MatDialog);
  const token = authService.getToken();

  const isAuthEndpoint = req.url === '/api/auth/login' || req.url === '/api/auth/register';

  if (!token || isAuthEndpoint || !req.url.startsWith('/api/')) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    catchError((error: unknown) => {
      // The server rejected our token: it was revoked (e.g. the user was blacklisted),
      // or it expired. Drop the session and send the user back to sign in.
      if (error instanceof HttpErrorResponse && error.status === 401 && authService.getToken() === token) {
        authService.clearSession();
        dialog.closeAll();
        router.navigate(['/login'], { queryParams: { signedOut: 1 } });
      }
      return throwError(() => error);
    }),
  );
};
