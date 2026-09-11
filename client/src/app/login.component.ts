import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthFacadeService } from './auth-facade.service';
import { Router } from '@angular/router';
import { StatusService } from './status.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  loading = false;
  form;

  constructor(
    private fb: FormBuilder,
    private auth: AuthFacadeService,
    private router: Router,
    public status: StatusService
  ) {
    this.form = this.fb.group({
      username: ['', [Validators.required]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  ngOnInit() {
    if (this.auth.getIsAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    this.loading = true;
    const { username, password } = this.form.value;
    this.auth.login(username!, password!);
    setTimeout(() => {
      this.loading = false;
      if (this.auth.getIsAuthenticated()) {
        this.router.navigate(['/dashboard']);
      }
    }, 300);
  }
}
