import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthFacadeService } from './auth-facade.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-first-run-setup',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './first-run-setup.component.html',
  styleUrl: './first-run-setup.component.css'
})
export class FirstRunSetupComponent {
  loading = false;
  form;

  constructor(private fb: FormBuilder, private auth: AuthFacadeService, private router: Router) {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    this.loading = true;
    const { email, password } = this.form.value;
    // Use email as username for backend compatibility
    this.auth.setupAdmin(email!, email!, password!).subscribe({
      next: () => {
        this.loading = false;
        localStorage.setItem('adminSetupDone', 'true');
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.loading = false;
        // Error handled by interceptor and StatusService
      }
    });
  }
}
