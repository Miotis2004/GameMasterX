import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthFacadeService } from './auth-facade.service';
import { StatusService } from './status.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(private auth: AuthFacadeService, private router: Router, private status: StatusService) {}

  canActivate() {
    if (this.status.unauthorized()) {
      this.status.setError('Session expired. Please log in again.');
      this.router.navigate(['/login']);
      return false;
    }
    if (this.auth.getIsAuthenticated()) {
      this.status.setUnauthorized(false);
      return true;
    }
    this.status.setUnauthorized(true);
    this.status.setError('Access denied. Please log in to continue.');
    this.router.navigate(['/login']);
    return false;
  }
}
