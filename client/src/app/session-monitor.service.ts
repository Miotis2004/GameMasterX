import { Injectable, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { StatusService } from './status.service';
import { Subscription } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SessionMonitorService implements OnDestroy {
  private sub?: Subscription;

  constructor(private status: StatusService, private router: Router) {
    // React to unauthorized changes
    this.sub = new Subscription();
    // Use polling via signal? Since signals are not observable, we can use effect-like pattern.
    // For simplicity, we check on navigation guards. We'll also expose a method.
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  // Called manually if needed
  checkAndRedirect() {
    if (this.status.unauthorized()) {
      this.status.setError('Session expired. Please log in again.');
      this.router.navigate(['/login']);
    }
  }
}
