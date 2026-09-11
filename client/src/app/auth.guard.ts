import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { AuthFacadeService } from './auth-facade.service';
import { HttpClient } from '@angular/common/http';
import { StatusService } from './status.service';
import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(private auth: AuthFacadeService, private router: Router, private status: StatusService, private http: HttpClient) {}

  canActivate(): Observable<boolean | UrlTree> {
    return this.http.get<{ authenticated: boolean; username?: string }>('http://localhost:5172/api/auth/session').pipe(
      map(response => {
        if (response.authenticated) {
          this.auth.isAuthenticated.set(true);
          if (response.username) {
            this.auth.username.set(response.username);
          }
          this.status.setUnauthorized(false);
          return true;
        } else {
          this.auth.isAuthenticated.set(false);
          this.auth.username.set(null);
          this.status.setUnauthorized(true);
          this.status.setError('Access denied. Please log in.');
          return this.router.createUrlTree(['/login']);
        }
      }),
      catchError(() => {
        // Fallback for network issues, let HTTP interceptor handle the error display
        return of(this.router.createUrlTree(['/login']));
      })
    );
  }
}
