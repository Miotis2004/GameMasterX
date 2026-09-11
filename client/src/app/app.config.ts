import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
//import { provideHttpClient } from '@angular/common/http';
//import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideHttpClient,  withInterceptorsFromDi,  HTTP_INTERCEPTORS} from '@angular/common/http';

import { routes } from './app.routes';
import { HttpErrorInterceptor } from './http-error.interceptor';
import { CsrfInterceptor } from './csrf.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
     provideBrowserGlobalErrorListeners(),
  provideRouter(routes),
  provideHttpClient(withInterceptorsFromDi()),
  { provide: HTTP_INTERCEPTORS, useClass: CsrfInterceptor, multi: true },
  { provide: HTTP_INTERCEPTORS, useClass: HttpErrorInterceptor, multi: true }
  ]
};
