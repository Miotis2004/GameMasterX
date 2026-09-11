import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { UserFacadeService } from './user-facade.service';
import { AuthFacadeService } from './auth-facade.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent implements OnInit {
  form;
  loading = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  constructor(private fb: FormBuilder, private userFacade: UserFacadeService, private auth: AuthFacadeService) {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]]
    });
  }

  ngOnInit() {
    // For demo, use a placeholder user id. In real app, derive from session.
    const demoUserId = 'demo-user-id';
    this.userFacade.setCurrentUserId(demoUserId);
    this.userFacade.loadUser(demoUserId);
    const user = this.userFacade.getUser();
    if (user) {
      this.form.patchValue({ username: user.username, email: user.email ?? '' });
    }
  }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const { username, email } = this.form.value;
    this.userFacade.updateProfile({ username: username ?? '', email: email ?? '' });
    this.errorMessage = this.userFacade.getError();
    this.successMessage = this.userFacade.getSuccess();
  }

  getError(controlName: string): string | null {
    const control = this.form.get(controlName);
    if (control && control.touched && control.invalid) {
      if (control.errors?.['required']) return `${controlName} is required`;
      if (control.errors?.['email']) return 'Email must be valid';
      if (control.errors?.['minlength']) return `${controlName} must be at least 3 characters`;
    }
    return null;
  }
}
