import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

interface DiagnosticEnvelope {
  status: string;
  timestamp: string;
  message: string;
  details: Record<string, unknown>;
}

@Component({
  selector: 'app-diagnostics',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './diagnostics.component.html',
  styleUrl: './diagnostics.component.css'
})
export class DiagnosticsComponent implements OnInit {
  private http = inject(HttpClient);

  loading = signal(true);
  error = signal<string | null>(null);

  liveness = signal<DiagnosticEnvelope | null>(null);
  readiness = signal<DiagnosticEnvelope | null>(null);
  application = signal<DiagnosticEnvelope | null>(null);
  mongodb = signal<DiagnosticEnvelope | null>(null);

  // Derived values
  get transactionCapable() {
    const mongo = this.mongodb();
    if (!mongo) return null;
    const details = mongo.details as any;
    return details?.transactionSupported;
  }

  get replicaSetMemberInfo() {
    const mongo = this.mongodb();
    if (!mongo) return null;
    const details = mongo.details as any;
    return details?.replicaSetMember;
  }

  get aiHealth() {
    const ready = this.readiness();
    if (!ready) return null;
    const details = ready.details as any;
    const ai = details?.ai;
    const provider = ai?.provider;
    return provider?.status || null;
  }

  get realTimeStatus() {
    const ready = this.readiness();
    return ready ? ready.status : null;
  }

  get assetStatus() {
    return 'UP';
  }

  async ngOnInit() {
    try {
      const [livenessData, readinessData, applicationData, mongodbData] = await Promise.all([
        firstValueFrom(this.http.get<DiagnosticEnvelope>('http://localhost:5172/health/liveness')),
        firstValueFrom(this.http.get<DiagnosticEnvelope>('http://localhost:5172/health/readiness')),
        firstValueFrom(this.http.get<DiagnosticEnvelope>('http://localhost:5172/api/diagnostics/application')),
        firstValueFrom(this.http.get<DiagnosticEnvelope>('http://localhost:5172/api/diagnostics/mongodb'))
      ]);

      this.liveness.set(livenessData);
      this.readiness.set(readinessData);
      this.application.set(applicationData);
      this.mongodb.set(mongodbData);
    } catch (e) {
      this.error.set('Failed to load diagnostics');
    } finally {
      this.loading.set(false);
    }
  }

  statusClass(status: string | undefined | null) {
    if (!status) return 'unknown';
    return status.toUpperCase() === 'UP' ? 'up' : 'down';
  }
}
