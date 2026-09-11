import { Injectable, signal } from '@angular/core';
import { UserApiService, UserResponse, UpdateUserRequest } from './user-api.service';
import { AuthFacadeService } from './auth-facade.service';

@Injectable({ providedIn: 'root' })
export class UserFacadeService {
  private readonly currentUserId = signal<string | null>(null);
  private readonly user = signal<UserResponse | null>(null);
  private readonly loading = signal(false);
  private readonly error = signal<string | null>(null);
  private readonly success = signal<string | null>(null);

  constructor(private api: UserApiService, private auth: AuthFacadeService) {}

  setCurrentUserId(id: string) {
    this.currentUserId.set(id);
  }

  getCurrentUserId() {
    return this.currentUserId();
  }

  getUser() {
    return this.user();
  }

  getLoading() {
    return this.loading();
  }

  getError() {
    return this.error();
  }

  getSuccess() {
    return this.success();
  }

  loadUser(id?: string) {
    const userId = id ?? this.currentUserId();
    if (!userId) {
      this.error.set('User ID not set');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);
    this.api.getUser(userId).subscribe({
      next: (u) => {
        this.user.set(u);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set('Failed to load user profile');
      }
    });
  }

  updateProfile(data: Partial<UpdateUserRequest>) {
    const userId = this.currentUserId();
    if (!userId) {
      this.error.set('User ID not set');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);
    this.api.updateUser(userId, data).subscribe({
      next: (u) => {
        this.user.set(u);
        this.loading.set(false);
        this.success.set('Profile updated successfully');
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to update profile');
      }
    });
  }

  changePassword(currentPassword: string, newPassword: string, confirmPassword: string) {
    const userId = this.currentUserId();
    if (!userId) {
      this.error.set('User ID not set');
      return;
    }
    // Client-side validation for current and new passwords
    if (!currentPassword || !newPassword) {
      this.error.set('Current and new passwords are required');
      return;
    }
    if (newPassword !== confirmPassword) {
      this.error.set('New password and confirmation do not match');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);
    this.api.updateUser(userId, { password: newPassword }).subscribe({
      next: (u) => {
        this.user.set(u);
        this.loading.set(false);
        this.success.set('Password changed successfully');
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to change password');
      }
    });
  }

  clearMessages() {
    this.error.set(null);
    this.success.set(null);
  }
}
