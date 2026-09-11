import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SetupAdminRequest {
  username: string;
  email: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly apiBase = 'http://localhost:5172/api';

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<void> {
    return this.http.post<void>(`${this.apiBase}/auth/login`, request, { withCredentials: true });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.apiBase}/auth/logout`, {}, { withCredentials: true });
  }

  setupAdmin(request: SetupAdminRequest): Observable<{ id: string; username: string; email: string; createdAt: string }> {
    return this.http.post<{ id: string; username: string; email: string; createdAt: string }>(
      `${this.apiBase}/admin/setup`,
      request
    );
  }
}
