import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { CommunicationFacadeService } from './communication-facade.service';

@Component({
  selector: 'app-campaign-chat',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './campaign-chat.component.html',
  styleUrl: './campaign-chat.component.css'
})
export class CampaignChatComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private facade = inject(CommunicationFacadeService);

  ngOnInit() {
    // CampaignId is from parent route param
    const campaignId = this.route.snapshot.paramMap.get('campaignId') ?? this.route.parent?.snapshot.paramMap.get('campaignId');
    if (campaignId) {
      // Default role for demo; in real app, role comes from membership service
      this.facade.setCampaign(campaignId, 'PLAYER');
    }
  }

  get connectionStatus() {
    return this.facade.connectionStatus();
  }

  get chatMessages() {
    return this.facade.chatMessages();
  }

  get whispers() {
    return this.facade.whispers();
  }

  get gmNotes() {
    return this.facade.gmNotes();
  }

  get systemEvents() {
    return this.facade.systemEvents();
  }
}
