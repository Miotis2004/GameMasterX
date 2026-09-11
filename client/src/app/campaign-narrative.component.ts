import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NarrativeStreamComponent } from './narrative-stream.component';
import { ActionInputComponent } from './action-input.component';
import { CampaignChatComponent } from './campaign-chat.component';
import { AiStatusComponent } from './ai-status.component';

@Component({
  selector: 'app-campaign-narrative',
  standalone: true,
  imports: [CommonModule, NarrativeStreamComponent, ActionInputComponent, CampaignChatComponent, AiStatusComponent],
  templateUrl: './campaign-narrative.component.html',
  styleUrl: './campaign-narrative.component.css'
})
export class CampaignNarrativeComponent {}
