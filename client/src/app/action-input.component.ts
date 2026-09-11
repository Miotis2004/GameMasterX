import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NarrativeFacadeService } from './narrative-facade.service';

@Component({
  selector: 'app-action-input',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './action-input.component.html',
  styleUrl: './action-input.component.css'
})
export class ActionInputComponent {
  private fb = new FormBuilder();
  private route = inject(ActivatedRoute);
  private facade = inject(NarrativeFacadeService);
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
    const actionValue = this.form.value.action;
    if (!actionValue) {
      this.submitting.set(false);
      return;
    }
    const campaignId = this.route.snapshot.paramMap.get('campaignId');
    if (!campaignId) {
      this.lastResult.set('No campaign selected');
      this.submitting.set(false);
      return;
    }
    this.facade.submitAction(actionValue).subscribe({
      next: () => {
        this.lastResult.set(`Action submitted: ${actionValue}`);
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
