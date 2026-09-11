import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CommunicationFacadeService } from './communication-facade.service';

@Component({
  selector: 'app-reactions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reactions.component.html',
  styleUrl: './reactions.component.css'
})
export class ReactionsComponent {
  @Input() campaignId: string | null = null;
  @Input() messageId: string | null = null;
  private facade = inject(CommunicationFacadeService);

  reactions = ['👍', '❤️', '😂', '😮'];

  addReaction(reaction: string) {
    if (!this.campaignId || !this.messageId) return;
    this.facade.addReaction(this.campaignId, this.messageId, reaction).subscribe();
  }
}
