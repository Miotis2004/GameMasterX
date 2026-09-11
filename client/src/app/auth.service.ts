import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { StatusService } from './status.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly isAuthenticated = signal(false);
  readonly username = signal<string | null>(null);

  private readonly apiBase = 'http://localhost:5172/api';

  constructor(private http: HttpClient, private status: StatusService) {}

  login(username: string, password: string) {
    this.http.post(`${this.apiBase}/auth/login`, { username, password }).subscribe({
      next: () => {
        this.isAuthenticated.set(true);
        this.username.set(username);
        this.status.setUnauthorized(false);
        this.status.setError(null);
      },
      error: () => {
        this.isAuthenticated.set(false);
        this.username.set(null);
      }
    });
  }

  logout() {
    this.http.post(`${this.apiBase}/auth/logout`, {}).subscribe({
      next: () => {
        this.isAuthenticated.set(false);
        this.username.set(null);
        this.status.setUnauthorized(false);
        this.status.setError(null);
      }
    });
  }

  setupAdmin(username: string, email: string, password: string) {
    return this.http.post(`${this.apiBase}/admin/setup`, { username, email, password });
  }
}
