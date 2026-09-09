import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatusService } from './status.service';

@Component({
  selector: 'app-status-bar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './status-bar.component.html',
  styleUrl: './status-bar.component.css'
})
export class StatusBarComponent {
  constructor(public status: StatusService) {}
}
