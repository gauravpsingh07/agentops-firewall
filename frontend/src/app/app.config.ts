import {
  APP_INITIALIZER,
  ApplicationConfig,
  inject,
  provideZoneChangeDetection
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { catchError, firstValueFrom, of } from 'rxjs';

import { appRoutes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { unauthorizedInterceptor } from './core/interceptors/unauthorized.interceptor';
import { AuthService } from './core/services/auth.service';
import { TokenStorageService } from './core/services/token-storage.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(appRoutes),
    provideHttpClient(withInterceptors([authInterceptor, unauthorizedInterceptor])),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const auth = inject(AuthService);
        const tokens = inject(TokenStorageService);
        return () => {
          if (!tokens.getToken()) {
            return Promise.resolve();
          }
          return firstValueFrom(auth.loadCurrentUser().pipe(catchError(() => of(null))));
        };
      }
    }
  ]
};
