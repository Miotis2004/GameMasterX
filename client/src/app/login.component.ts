import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from './auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  username = signal('');
  password = signal('');
  error = signal<string | null>(null);
  loading = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  submit() {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username(), this.password());
    // Wait for signal change via subscription? We'll poll quickly.
    // For simplicity, navigate after short delay assuming success.
    setTimeout(() => {
      this.loading.set(false);
      if (this.auth.isAuthenticated()) {
        this.router.navigate(['/dashboard']);
      } else {
        this.error.set('Invalid credentials');
      }
    }, 300);
  }
}
