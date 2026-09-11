import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { UserFacadeService } from './user-facade.service';

@Component({
  selector: 'app-password-change',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './password-change.component.html',
  styleUrl: './password-change.component.css'
})
export class PasswordChangeComponent implements OnInit {
  form;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  constructor(private fb: FormBuilder, private userFacade: UserFacadeService) {
    this.form = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordsMatchValidator });
  }

  ngOnInit() {
    const demoUserId = 'demo-user-id';
    this.userFacade.setCurrentUserId(demoUserId);
  }

  passwordsMatchValidator(group: any) {
    const newPass = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return newPass && confirm && newPass === confirm ? null : { mismatch: true };
  }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.form.value;
    this.userFacade.changePassword(currentPassword!, newPassword!, confirmPassword!);
    this.errorMessage = this.userFacade.getError();
    this.successMessage = this.userFacade.getSuccess();
  }

  getError(controlName: string): string | null {
    const control = this.form.get(controlName);
    if (control && control.touched && control.invalid) {
      if (control.errors?.['required']) return `${controlName} is required`;
      if (control.errors?.['minlength']) return 'Password must be at least 8 characters';
    }
    if (controlName === 'confirmPassword' && this.form.hasError('mismatch')) {
      return 'Passwords do not match';
    }
    return null;
  }
}
