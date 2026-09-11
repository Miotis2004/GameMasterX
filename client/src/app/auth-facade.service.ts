import { Injectable, signal } from '@angular/core';
import { AuthApiService, LoginRequest, SetupAdminRequest } from './auth-api.service';
import { StatusService } from './status.service';

@Injectable({ providedIn: 'root' })
export class AuthFacadeService {
  readonly isAuthenticated = signal(false);
  readonly username = signal<string | null>(null);

  constructor(private api: AuthApiService, private status: StatusService) {}

  login(username: string, password: string) {
    const req: LoginRequest = { username, password };
    this.api.login(req).subscribe({
      next: () => {
        this.isAuthenticated.set(true);
        this.username.set(username);
        this.status.setUnauthorized(false);
        this.status.setError(null);
      },
      error: () => {
        this.isAuthenticated.set(false);
        this.username.set(null);
        this.status.setUnauthorized(true);
        this.status.setError('Invalid credentials. Please try again.');
      }
    });
  }

  logout() {
    this.api.logout().subscribe({
      next: () => {
        this.isAuthenticated.set(false);
        this.username.set(null);
        this.status.setUnauthorized(false);
        this.status.setError(null);
      },
      error: () => {
        this.isAuthenticated.set(false);
        this.username.set(null);
      }
    });
  }

  setupAdmin(username: string, email: string, password: string) {
    const req: SetupAdminRequest = { username, email, password };
    return this.api.setupAdmin(req);
  }

  getIsAuthenticated() {
    return this.isAuthenticated();
  }

  getUsername() {
    return this.username();
  }
}
