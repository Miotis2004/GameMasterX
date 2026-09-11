import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent
} from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { HttpClient } from '@angular/common/http';

@Injectable()
export class CsrfInterceptor implements HttpInterceptor {
  constructor(private http: HttpClient) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Centrally apply withCredentials to all backend API requests
    const isApiRequest = req.url.startsWith('http://localhost:5172/api');

    let clonedReq = req;
    if (isApiRequest) {
      clonedReq = req.clone({
        withCredentials: true
      });
    }

    const safeMethods = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

    // Don't intercept requests that aren't to our API, are safe methods, or are requests for the CSRF token itself
    if (!isApiRequest || safeMethods.includes(req.method) || req.url.endsWith('/api/csrf/token')) {
      return next.handle(clonedReq);
    }

    // For unsafe methods, fetch the CSRF token first, then append it
    return this.http.get<{ csrfToken: string }>('http://localhost:5172/api/csrf/token', { withCredentials: true })
      .pipe(
        switchMap(response => {
          const finalReq = clonedReq.clone({
            setHeaders: {
              'X-CSRF-Token': response.csrfToken
            }
          });
          return next.handle(finalReq);
        })
      );
  }
}
