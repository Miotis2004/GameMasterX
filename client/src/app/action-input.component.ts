import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-action-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './action-input.component.html',
  styleUrl: './action-input.component.css'
})
export class ActionInputComponent {
  private fb = new FormBuilder();
  private http = inject(HttpClient);
  readonly form = this.fb.group({
    action: ['', Validators.required]
  });
  readonly submitting = signal(false);
  readonly lastResult = signal<string | null>(null);

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const action = this.form.value.action;
    this.http.post('http://localhost:5172/api/actions', { action }).subscribe({
      next: () => {
        this.lastResult.set(`Submitted: ${action}`);
        this.submitting.set(false);
        this.form.reset();
      },
      error: () => {
        this.lastResult.set('Submission failed');
        this.submitting.set(false);
      }
    });
  }
}
