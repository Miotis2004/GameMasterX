import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { StatusService } from './status.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(private auth: AuthService, private router: Router, private status: StatusService) {}

  canActivate() {
    if (this.auth.isAuthenticated()) {
      this.status.setUnauthorized(false);
      return true;
    }
    this.status.setUnauthorized(true);
    this.status.setError('Access denied. Please log in to continue.');
    this.router.navigate(['/login']);
    return false;
  }
}
