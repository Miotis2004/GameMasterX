import { Component, signal, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CampaignEventsService } from './campaign-events.service';

@Component({
  selector: 'app-narrative-stream',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './narrative-stream.component.html',
  styleUrl: './narrative-stream.component.css'
})
export class NarrativeStreamComponent implements OnInit {
  private eventsService = inject(CampaignEventsService);
  readonly narration = signal<string>('');

  ngOnInit(): void {
    // Simple demo: accumulate narrative deltas from events
    this.eventsService.events$.subscribe(event => {
      if (event.type === 'narrative_delta' && event.payload?.delta) {
        this.narration.update(current => current + event.payload.delta);
      }
    });
  }
}
