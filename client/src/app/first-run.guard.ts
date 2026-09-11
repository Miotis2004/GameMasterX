import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class FirstRunGuard implements CanActivate {
  constructor(private router: Router, private http: HttpClient) {}

  canActivate(): Observable<boolean | UrlTree> {
    return this.http.get<{ setupRequired: boolean }>('http://localhost:5172/api/admin/setup/status').pipe(
      map(response => {
        if (response.setupRequired) {
          return this.router.createUrlTree(['/setup']);
        }
        return true;
      }),
      catchError(() => {
        // If the backend fails, default to allowing it to pass and let other guards/errors handle it
        return of(true);
      })
    );
  }
}
