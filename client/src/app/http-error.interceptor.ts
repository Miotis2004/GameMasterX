import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { StatusService } from './status.service';

@Injectable()
export class HttpErrorInterceptor implements HttpInterceptor {
  constructor(private status: StatusService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    this.status.setLoading(true);
    this.status.clearAll();
    return next.handle(req).pipe(
      finalize(() => this.status.setLoading(false)),
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 || error.status === 403) {
          this.status.setUnauthorized(true);
          this.status.setError('You are not authorized to access this resource. Please log in again.');
          return throwError(() => error);
        }
        if (error.status === 0) {
          this.status.setDisconnected(true);
          this.status.setError('Unable to connect to the server. Please check your network connection.');
          return throwError(() => error);
        }
        if (error.status >= 500) {
          this.status.setDisconnected(true);
          this.status.setError('Service is temporarily unavailable. Please try again later.');
          return throwError(() => error);
        }
        if (error.status === 400) {
          const body = error.error;
          if (body && body.fieldErrors && Array.isArray(body.fieldErrors)) {
            this.status.setValidationErrors(body.fieldErrors);
            this.status.setError('Please correct the highlighted fields.');
          } else {
            const message = body?.message || 'Invalid request. Please check your input.';
            this.status.setError(message);
          }
          return throwError(() => error);
        }
        const message = error.error?.message || 'An unexpected error occurred. Please try again.';
        this.status.setError(message);
        return throwError(() => error);
      })
    );
  }
}
