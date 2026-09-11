import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { NarrativeFacadeService } from './narrative-facade.service';

@Component({
  selector: 'app-narrative-stream',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './narrative-stream.component.html',
  styleUrl: './narrative-stream.component.css'
})
export class NarrativeStreamComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private facade = inject(NarrativeFacadeService);

  ngOnInit(): void {
    const campaignId = this.route.snapshot.paramMap.get('campaignId');
    if (campaignId) {
      this.facade.setCampaign(campaignId);
      this.facade.startStream(campaignId, {});
    }
  }

  ngOnDestroy(): void {
    this.facade.cancelStream();
  }

  get narration() {
    return this.facade.narrativeHistory();
  }

  get streaming() {
    return this.facade.streaming();
  }
}
