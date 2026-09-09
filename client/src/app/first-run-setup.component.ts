import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from './auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-first-run-setup',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './first-run-setup.component.html',
  styleUrl: './first-run-setup.component.css'
})
export class FirstRunSetupComponent {
  username = signal('');
  email = signal('');
  password = signal('');
  error = signal<string | null>(null);
  loading = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  submit() {
    this.loading.set(true);
    this.error.set(null);
    this.auth.setupAdmin(this.username(), this.email(), this.password()).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.error || 'Setup failed');
      }
    });
  }
}
