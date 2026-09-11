import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class FirstRunGuard implements CanActivate {
  constructor(private router: Router) {}

  canActivate() {
    const setupDone = localStorage.getItem('adminSetupDone');
    if (!setupDone) {
      this.router.navigate(['/setup']);
      return false;
    }
    return true;
  }
}
