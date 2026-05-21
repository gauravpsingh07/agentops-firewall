import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { TokenStorageService } from '../services/token-storage.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(TokenStorageService);
  const token = tokens.getToken();
  if (!token) {
    return next(req);
  }
  const authorized = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` }
  });
  return next(authorized);
};
