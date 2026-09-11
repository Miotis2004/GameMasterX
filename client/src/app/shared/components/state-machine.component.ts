import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

export type UiState =
  | 'loading'
  | 'empty'
  | 'success'
  | 'validation'
  | 'disconnected'
  | 'reconnecting'
  | 'unauthorized'
  | 'forbidden'
  | 'conflict'
  | 'degraded-ai'
  | 'failure';

@Component({
  selector: 'app-state-machine',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './state-machine.component.html',
  styleUrl: './state-machine.component.css'
})
export class StateMachineComponent {
  state = input<UiState>('loading');
  title = input<string>('');
  message = input<string>('');
  details = input<string>('');

  get isLoading() {
    return this.state() === 'loading';
  }
  get isEmpty() {
    return this.state() === 'empty';
  }
  get isSuccess() {
    return this.state() === 'success';
  }
  get isValidation() {
    return this.state() === 'validation';
  }
  get isDisconnected() {
    return this.state() === 'disconnected';
  }
  get isReconnecting() {
    return this.state() === 'reconnecting';
  }
  get isUnauthorized() {
    return this.state() === 'unauthorized';
  }
  get isForbidden() {
    return this.state() === 'forbidden';
  }
  get isConflict() {
    return this.state() === 'conflict';
  }
  get isDegradedAi() {
    return this.state() === 'degraded-ai';
  }
  get isFailure() {
    return this.state() === 'failure';
  }
}
