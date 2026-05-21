import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { TokenStorageService } from '../services/token-storage.service';

export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const tokens = inject(TokenStorageService);
  return next(req).pipe(
    catchError((err) => {
      if (
        err instanceof HttpErrorResponse &&
        err.status === 401 &&
        !req.url.endsWith('/auth/login')
      ) {
        tokens.clear();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    })
  );
};
