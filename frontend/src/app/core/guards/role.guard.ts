import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { UserRole } from '../models/user-role';
import { AuthService } from '../services/auth.service';

export function roleGuard(...allowed: UserRole[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const user = auth.currentUser;
    if (user && allowed.includes(user.role)) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };
}
