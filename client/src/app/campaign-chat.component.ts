import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

interface ChatMessage {
  id: string;
  author: string;
  role: string;
  text: string;
}

@Component({
  selector: 'app-campaign-chat',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './campaign-chat.component.html',
  styleUrl: './campaign-chat.component.css'
})
export class CampaignChatComponent {
  readonly messages = signal<ChatMessage[]>([
    { id: '1', author: 'GM', role: 'GAME_MASTER', text: 'Welcome to the campaign' },
    { id: '2', author: 'Player1', role: 'PLAYER', text: 'Hello!' }
  ]);
  readonly roleFilter = signal<string>('ALL');

  filteredMessages() {
    const filter = this.roleFilter();
    if (filter === 'ALL') return this.messages();
    return this.messages().filter(m => m.role === filter);
  }

  setFilter(role: string) {
    this.roleFilter.set(role);
  }
}
