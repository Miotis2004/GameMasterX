import { Injectable, signal } from '@angular/core';

export interface FieldError {
  field: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class StatusService {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly unauthorized = signal(false);
  readonly disconnected = signal(false);
  readonly validationErrors = signal<FieldError[]>([]);

  setLoading(value: boolean) {
    this.loading.set(value);
  }

  setError(message: string | null) {
    this.errorMessage.set(message);
  }

  setUnauthorized(value: boolean) {
    this.unauthorized.set(value);
  }

  setDisconnected(value: boolean) {
    this.disconnected.set(value);
  }

  setValidationErrors(errors: FieldError[]) {
    this.validationErrors.set(errors);
  }

  clearAll() {
    this.loading.set(false);
    this.errorMessage.set(null);
    this.unauthorized.set(false);
    this.disconnected.set(false);
    this.validationErrors.set([]);
  }
}
