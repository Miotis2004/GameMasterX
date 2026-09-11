import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UserResponse {
  id: string;
  schemaVersion: number;
  revision: number;
  createdAt: string;
  updatedAt: string;
  username: string;
  email: string | null;
}

export interface UpdateUserRequest {
  username?: string;
  email?: string;
  password?: string;
}

@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly apiBase = 'http://localhost:5172/api';

  constructor(private http: HttpClient) {}

  getUser(id: string): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.apiBase}/users/${encodeURIComponent(id)}`);
  }

  updateUser(id: string, data: UpdateUserRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.apiBase}/users/${encodeURIComponent(id)}`, data);
  }
}
