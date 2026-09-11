import { Component, signal, OnInit } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';
import { StatusBarComponent } from './status-bar.component';
import { StatusService } from './status.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, StatusBarComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  protected readonly title = signal('game-master-x-client');

  constructor(private status: StatusService, private router: Router) {}

  ngOnInit() {
  }
}
