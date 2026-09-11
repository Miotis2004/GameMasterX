import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { CommunicationFacadeService } from './communication-facade.service';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-polls',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './polls.component.html',
  styleUrl: './polls.component.css'
})
export class PollsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private facade = inject(CommunicationFacadeService);
  private http = inject(HttpClient);

  campaignId: string | null = null;
  question = '';
  options: string[] = [''];
  polls: any[] = [];

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('campaignId') ?? this.route.parent?.snapshot.paramMap.get('campaignId') ?? null;
    this.campaignId = id;
    if (this.campaignId) {
      this.loadPolls();
    }
  }

  loadPolls() {
    if (!this.campaignId) return;
    this.http.get<any[]>(`http://localhost:5172/api/campaigns/${this.campaignId}/polls`, { withCredentials: true }).subscribe({
      next: (data) => {
        this.polls = data;
      }
    });
  }

  addOption() {
    this.options.push('');
  }

  createPoll() {
    if (!this.campaignId || !this.question.trim()) return;
    const opts = this.options.filter(o => o.trim());
    this.facade.createPoll(this.campaignId, { question: this.question, options: opts }).subscribe({
      next: () => {
        this.question = '';
        this.options = [''];
        this.loadPolls();
      }
    });
  }

  vote(pollId: string, index: number) {
    if (!this.campaignId) return;
    this.facade.votePoll(this.campaignId, pollId, index).subscribe({
      next: () => this.loadPolls()
    });
  }

  closePoll(pollId: string) {
    if (!this.campaignId) return;
    this.facade.closePoll(this.campaignId, pollId).subscribe({
      next: () => this.loadPolls()
    });
  }
}
