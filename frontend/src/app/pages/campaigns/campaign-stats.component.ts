import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { WebSocketService } from '../../services/websocket.service';
import { Campaign, CampaignStats } from '../../models/models';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-campaign-stats',
  standalone: true,
  imports: [CommonModule, RouterLink],
  styles: [`
    .metrics-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:16px; margin-bottom:24px; }
    .metric-card {
      background:white; border-radius:10px; padding:20px;
      box-shadow:var(--shadow); text-align:center;
      .metric-value { font-size:32px; font-weight:800; }
      .metric-label { font-size:12px; color:var(--gray-400); text-transform:uppercase; letter-spacing:0.5px; margin-top:4px; }
      .metric-rate { font-size:14px; font-weight:600; margin-top:4px; }
    }
    .funnel {
      background:white; border-radius:10px; padding:24px; box-shadow:var(--shadow);
      .funnel-step {
        display:flex; align-items:center; gap:16px; margin-bottom:16px;
        .funnel-label { width:100px; font-size:13px; font-weight:500; color:var(--gray-600); flex-shrink:0; }
        .funnel-bar { flex:1;
          .bar-fill { height:32px; border-radius:6px; display:flex; align-items:center; padding:0 12px;
            color:white; font-size:13px; font-weight:600; transition:width 0.5s; }
        }
        .funnel-count { width:80px; text-align:right; font-size:14px; font-weight:700; color:var(--gray-700); }
        .funnel-pct { width:60px; text-align:right; font-size:13px; color:var(--gray-400); }
      }
    }
    .live-indicator { display:flex; align-items:center; gap:6px; font-size:13px; color:var(--success);
      .dot { width:8px; height:8px; border-radius:50%; background:var(--success); animation:pulse 1.5s infinite; }
    }
    @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.3} }
    .progress-section { background:white; border-radius:10px; padding:24px; box-shadow:var(--shadow); }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">{{ campaign?.name }}</div>
          <div class="page-subtitle">
            Campaign Analytics
            @if (stats?.status === 'SENDING') {
              <span class="live-indicator" style="display:inline-flex;margin-left:12px">
                <span class="dot"></span> Live
              </span>
            }
          </div>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" (click)="exportReport()">
            <i class="fas fa-download"></i> Summary CSV
          </button>
          <button class="btn btn-secondary" (click)="exportRecipients()">
            <i class="fas fa-users"></i> Per-Recipient CSV
          </button>
          <a routerLink="/campaigns" class="btn btn-secondary">
            <i class="fas fa-arrow-left"></i> Back
          </a>
        </div>
      </div>

      @if (loading) {
        <div style="text-align:center;padding:60px"><div class="spinner spinner-dark"></div></div>
      } @else if (stats) {

        <!-- Status + Progress -->
        @if (stats.status === 'SENDING' || stats.status === 'PAUSED') {
          <div class="progress-section" style="margin-bottom:20px">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
              <span style="font-size:14px;font-weight:600">Sending Progress</span>
              <span style="font-size:13px;color:var(--gray-500)">
                {{ stats.sentCount + stats.failedCount }} / {{ stats.totalRecipients }}
                ({{ progress | number:'1.0-0' }}%)
              </span>
            </div>
            <div class="progress-bar" style="height:10px">
              <div class="progress-fill" [style.width.%]="progress"
                   [class.warning]="stats.status === 'PAUSED'"></div>
            </div>
            @if (statusMessage) {
              <div style="font-size:12px;color:var(--warning);margin-top:8px;font-weight:500">
                ⚠ {{ statusMessage }}
              </div>
            }
            @if (!wsConnected) {
              <div style="font-size:12px;color:var(--gray-400);margin-top:6px">
                ⚡ Live updates disconnected — polling every 10s
              </div>
            }
          </div>
        }

        <!-- Metrics -->
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-value" style="color:var(--primary)">{{ stats.sentCount | number }}</div>
            <div class="metric-label">Sent</div>
          </div>
          <div class="metric-card">
            <div class="metric-value" style="color:var(--success)">{{ stats.openCount | number }}</div>
            <div class="metric-label">Opened</div>
            <div class="metric-rate" style="color:var(--success)">{{ stats.openRate | number:'1.1-1' }}%</div>
          </div>
          <div class="metric-card">
            <div class="metric-value" style="color:var(--secondary)">{{ stats.clickCount | number }}</div>
            <div class="metric-label">Clicked</div>
            <div class="metric-rate" style="color:var(--secondary)">{{ stats.clickRate | number:'1.1-1' }}%</div>
          </div>
          <div class="metric-card">
            <div class="metric-value" style="color:var(--danger)">{{ stats.bounceCount | number }}</div>
            <div class="metric-label">Bounced</div>
            <div class="metric-rate" style="color:var(--danger)">{{ stats.bounceRate | number:'1.1-1' }}%</div>
          </div>
          <div class="metric-card">
            <div class="metric-value" style="color:var(--warning)">{{ stats.unsubscribeCount | number }}</div>
            <div class="metric-label">Unsubscribed</div>
          </div>
          <div class="metric-card">
            <div class="metric-value" style="color:var(--danger)">{{ stats.complaintCount | number }}</div>
            <div class="metric-label">Complaints</div>
          </div>
          <div class="metric-card">
            <div class="metric-value" style="color:var(--gray-500)">{{ stats.failedCount | number }}</div>
            <div class="metric-label">Failed</div>
          </div>
          <div class="metric-card">
            <div class="metric-value" style="color:var(--info)">{{ stats.pendingCount | number }}</div>
            <div class="metric-label">Pending</div>
          </div>
        </div>

        <!-- Funnel chart -->
        <div class="funnel">
          <h3 style="margin-bottom:20px;font-size:16px">Email Funnel</h3>
          @for (step of funnelSteps; track step.label) {
            <div class="funnel-step">
              <div class="funnel-label">{{ step.label }}</div>
              <div class="funnel-bar">
                <div class="bar-fill" [style.width.%]="step.pct" [style.background]="step.color"
                     [style.min-width]="step.count > 0 ? '60px' : '0'">
                  @if (step.count > 0) { {{ step.pct | number:'1.0-0' }}% }
                </div>
              </div>
              <div class="funnel-count">{{ step.count | number }}</div>
            </div>
          }
        </div>

        <!-- Benchmark comparison -->
        <div class="card" style="margin-top:20px">
          <div class="card-header"><h3>Benchmark Comparison</h3></div>
          <table class="data-table">
            <thead><tr><th>Metric</th><th>Your Rate</th><th>Industry Avg</th><th>Status</th></tr></thead>
            <tbody>
              <tr>
                <td>Open Rate</td>
                <td><strong>{{ stats.openRate | number:'1.1-1' }}%</strong></td>
                <td>15–25%</td>
                <td><span class="badge" [class.badge-success]="stats.openRate >= 15" [class.badge-warning]="stats.openRate < 15">
                  {{ stats.openRate >= 25 ? 'Excellent' : stats.openRate >= 15 ? 'Good' : 'Below Avg' }}
                </span></td>
              </tr>
              <tr>
                <td>Click Rate</td>
                <td><strong>{{ stats.clickRate | number:'1.1-1' }}%</strong></td>
                <td>2–5%</td>
                <td><span class="badge" [class.badge-success]="stats.clickRate >= 2" [class.badge-warning]="stats.clickRate < 2">
                  {{ stats.clickRate >= 5 ? 'Excellent' : stats.clickRate >= 2 ? 'Good' : 'Below Avg' }}
                </span></td>
              </tr>
              <tr>
                <td>Bounce Rate</td>
                <td><strong>{{ stats.bounceRate | number:'1.1-1' }}%</strong></td>
                <td>&lt;2%</td>
                <td><span class="badge" [class.badge-success]="stats.bounceRate < 2" [class.badge-danger]="stats.bounceRate >= 5" [class.badge-warning]="stats.bounceRate >= 2 && stats.bounceRate < 5">
                  {{ stats.bounceRate < 2 ? 'Good' : stats.bounceRate < 5 ? 'Warning' : 'Critical' }}
                </span></td>
              </tr>
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class CampaignStatsComponent implements OnInit, OnDestroy {
  campaignId!: number;
  campaign: Campaign | null = null;
  stats: CampaignStats | null = null;
  loading = true;
  liveProgress: any = null;
  wsConnected = false;
  statusMessage = '';
  private wsSubscription?: Subscription;
  private wsConnectedSub?: Subscription;
  private pollInterval: any;

  funnelSteps: any[] = [];

  constructor(
    private api: ApiService,
    private toast: ToastService,
    private route: ActivatedRoute,
    private wsService: WebSocketService
  ) {}

  ngOnInit(): void {
    this.campaignId = +this.route.snapshot.params['id'];
    this.loadStats();

    this.wsService.connect();

    this.wsConnectedSub = this.wsService.connected$.subscribe(connected => {
      this.wsConnected = connected;
      if (connected) this.wsService.subscribeToCampaignProgress(this.campaignId);
    });

    this.wsSubscription = this.wsService.campaignProgress$.subscribe(data => {
      if (data.campaignId === this.campaignId) {
        this.liveProgress = data;
        if (data.statusMessage) this.statusMessage = data.statusMessage;
        this.loadStats();
      }
    });

    this.wsService.subscribeToCampaignProgress(this.campaignId);

    // Polling fallback: runs always; WebSocket updates just make it more responsive
    this.pollInterval = setInterval(() => {
      if (this.stats?.status === 'SENDING' || this.stats?.status === 'PAUSED') {
        this.loadStats();
      }
    }, 10000);
  }

  ngOnDestroy(): void {
    this.wsSubscription?.unsubscribe();
    this.wsConnectedSub?.unsubscribe();
    this.wsService.unsubscribeFromCampaign(this.campaignId);
    clearInterval(this.pollInterval);
  }

  loadStats(): void {
    this.api.getCampaignStats(this.campaignId).subscribe({
      next: (s) => {
        this.stats = s;
        this.loading = false;
        this.buildFunnel(s);
      },
      error: () => this.loading = false
    });
    this.api.getCampaign(this.campaignId).subscribe(c => this.campaign = c);
  }

  buildFunnel(s: CampaignStats): void {
    const total = s.totalRecipients || 1;
    this.funnelSteps = [
      { label: 'Total', count: s.totalRecipients, pct: 100, color: '#94A3B8' },
      { label: 'Sent', count: s.sentCount, pct: s.sentCount / total * 100, color: '#3B82F6' },
      { label: 'Opened', count: s.openCount, pct: s.openCount / total * 100, color: '#10B981' },
      { label: 'Clicked', count: s.clickCount, pct: s.clickCount / total * 100, color: '#8B5CF6' },
      { label: 'Bounced', count: s.bounceCount, pct: s.bounceCount / total * 100, color: '#EF4444' },
      { label: 'Unsub', count: s.unsubscribeCount, pct: s.unsubscribeCount / total * 100, color: '#F59E0B' },
    ];
  }

  get progress(): number {
    if (!this.stats?.totalRecipients) return 0;
    return (this.stats.sentCount + this.stats.failedCount) / this.stats.totalRecipients * 100;
  }

  exportReport(): void {
    this.api.exportCampaignReport(this.campaignId).subscribe({
      next: (blob) => this.downloadBlob(blob, `campaign-${this.campaignId}-report.csv`),
      error: () => this.toast.error('Export failed. Please try again.')
    });
  }

  exportRecipients(): void {
    this.api.exportPerRecipientReport(this.campaignId).subscribe({
      next: (blob) => this.downloadBlob(blob, `campaign-${this.campaignId}-recipients.csv`),
      error: () => this.toast.error('Export failed. Please try again.')
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
