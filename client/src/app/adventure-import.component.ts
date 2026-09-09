import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AdventureService, AdventureImportError, toImportError } from './adventure.service';

/**
 * The import screen uploads a local adventure package (a ZIP archive containing
 * an {@code adventure.json} manifest) to the backend. Import errors returned by
 * the API — validation failures, path/size problems and id collisions — are
 * surfaced to the user, including the offending package location when the
 * server reports one.
 */
@Component({
  selector: 'app-adventure-import',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './adventure-import.component.html',
  styleUrl: './adventure-import.component.css',
})
export class AdventureImportComponent {
  private readonly service = inject(AdventureService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  private selectedFile: File | null = null;
  readonly selectedFileLabel = signal<string | null>(null);

  readonly importing = signal(false);
  readonly uploading = signal(false);
  readonly progress = signal(0);
  readonly error = signal<AdventureImportError | null>(null);
  readonly successId = signal<string | null>(null);

  readonly importForm = this.formBuilder.group({
    overwrite: [false],
  });

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (files && files.length > 0) {
      this.selectedFile = files[0];
      this.selectedFileLabel.set(files[0].name);
      this.error.set(null);
    } else {
      this.selectedFile = null;
      this.selectedFileLabel.set(null);
    }
    input.value = '';
  }

  submit(): void {
    if (!this.selectedFile) {
      this.error.set({
        status: 0,
        message: 'Please choose a ZIP package file to import.',
      });
      return;
    }

    this.importing.set(true);
    this.uploading.set(true);
    this.progress.set(0);
    this.error.set(null);
    this.successId.set(null);

    const overwrite = this.importForm.get('overwrite')?.value ?? false;

    this.service
      .importAdventureProgress(this.selectedFile, overwrite)
      .subscribe({
        next: (event) => {
          // Upload progress events (event.type === 3).
          if (event.type === 3) {
            const total = event.total ?? 0;
            this.progress.set(total ? Math.round((event.loaded * 100) / total) : 100);
          }
        },
        complete: () => {
          this.uploading.set(false);
          this.importing.set(false);
          this.progress.set(100);
        },
        error: (err: unknown) => {
          this.uploading.set(false);
          this.importing.set(false);
          this.error.set(toImportError(err));
        },
      });
  }

  retry(): void {
    this.submit();
  }

  goLibrary(): void {
    this.router.navigate(['/adventures']);
  }
}
