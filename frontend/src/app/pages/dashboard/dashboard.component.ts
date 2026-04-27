import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { DashboardStats } from '../../models/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  styles: [`
    .page-content { padding: 24px; }
    .welcome-bar {
      background: linear-gradient(135deg, #3B82F6, #8B5CF6);
      color: white;
      border-radius: 12px;
      padding: 24px 28px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 24px;
      h2 { font-size: 20px; font-weight: 700; }
      p { opacity: 0.85; font-size: 14px; margin-top: 4px; }
    }
    .rate-cards {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
      margin-bottom: 24px;
    }
    .rate-card {
      background: white;
      border-radius: 10px;
      padding: 20px;
      box-shadow: var(--shadow);
      text-align: center;
      .rate-value {
        font-size: 28px; font-weight: 700;
        &.good { color: var(--success); }
        &.ok { color: var(--warning); }
        &.bad { color: var(--danger); }
      }
      .rate-label { font-size: 13px; color: var(--gray-500); margin-top: 4px; }
      .rate-bar { margin-top: 10px; }
    }
    .campaigns-table-card {
      background: white;
      border-radius: 10px;
      box-shadow: var(--shadow);
    }
    .status-dot {
      display: inline-block;
      width: 8px; height: 8px;
      border-radius: 50%;
      margin-right: 6px;
      &.sending { background: var(--primary); animation: pulse 1.5s infinite; }
      &.completed { background: var(--success); }
      &.paused { background: var(--warning); }
      &.draft { background: var(--gray-400); }
      &.failed { background: var(--danger); }
      &.cancelled { background: var(--gray-400); }
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.4; }
    }
    .loading-overlay {
      display: flex; align-items: center; justify-content: center;
      padding: 60px;
    }
  `],
  template: `
    <div class="page-content">
      <!-- Welcome bar -->
      <div class="welcome-bar">
        <div>
          <h2>Welcome to Bulk Email Pro</h2>
          <p>Send thousands of emails with confidence. Track, analyze, and optimize your campaigns.</p>
        </div>
        <a routerLink="/campaigns/new" class="btn btn-primary" style="background:rgba(255,255,255,0.2);border:1px solid rgba(255,255,255,0.4)">
          <i class="fas fa-plus"></i> New Campaign
        </a>
      </div>

      <!-- Stats grid -->
      @if (loading) {
        <div class="loading-overlay"><div class="spinner spinner-dark"></div></div>
      } @else if (stats) {
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon blue"><i class="fas fa-users"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalContacts | number }}</div>
              <div class="stat-label">Total Contacts</div>
              <div class="stat-change positive">{{ stats.activeContacts | number }} active</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon purple"><i class="fas fa-paper-plane"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalCampaigns }}</div>
              <div class="stat-label">Total Campaigns</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon green"><i class="fas fa-envelope-open"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalSent | number }}</div>
              <div class="stat-label">Total Emails Sent</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon cyan"><i class="fas fa-eye"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalOpens | number }}</div>
              <div class="stat-label">Total Opens</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon orange"><i class="fas fa-mouse-pointer"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalClicks | number }}</div>
              <div class="stat-label">Total Clicks</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon red"><i class="fas fa-exclamation-triangle"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalBounces | number }}</div>
              <div class="stat-label">Total Bounces</div>
            </div>
          </div>
        </div>

        <!-- Rate cards -->
        <div class="rate-cards">
          <div class="rate-card">
            <div class="rate-value" [class]="getRateClass(stats.avgOpenRate, 15, 25)">
              {{ stats.avgOpenRate | number:'1.1-1' }}%
            </div>
            <div class="rate-label">Avg. Open Rate</div>
            <div class="rate-bar progress-bar">
              <div class="progress-fill" [class]="getRateClass(stats.avgOpenRate, 15, 25)"
                   [style.width.%]="Math.min(stats.avgOpenRate, 100)"></div>
            </div>
          </div>
          <div class="rate-card">
            <div class="rate-value" [class]="getRateClass(stats.avgClickRate, 2, 5)">
              {{ stats.avgClickRate | number:'1.1-1' }}%
            </div>
            <div class="rate-label">Avg. Click Rate</div>
            <div class="rate-bar progress-bar">
              <div class="progress-fill" [class]="getRateClass(stats.avgClickRate, 2, 5)"
                   [style.width.%]="Math.min(stats.avgClickRate * 10, 100)"></div>
            </div>
          </div>
          <div class="rate-card">
            <div class="rate-value" [class]="getBounceClass(stats.avgBounceRate)">
              {{ stats.avgBounceRate | number:'1.1-1' }}%
            </div>
            <div class="rate-label">Avg. Bounce Rate</div>
            <div class="rate-bar progress-bar">
              <div class="progress-fill" [class]="getBounceClass(stats.avgBounceRate)"
                   [style.width.%]="Math.min(stats.avgBounceRate * 20, 100)"></div>
            </div>
          </div>
        </div>

        <!-- Recent campaigns -->
        <div class="campaigns-table-card">
          <div class="card-header" style="padding: 16px 20px; border-bottom: 1px solid var(--gray-100); display:flex; align-items:center; justify-content:space-between;">
            <h3>Recent Campaigns</h3>
            <a routerLink="/campaigns" class="btn btn-sm btn-outline">View All</a>
          </div>
          @if (stats.recentCampaigns?.length) {
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Campaign</th>
                    <th>Status</th>
                    <th>Sent</th>
                    <th>Open Rate</th>
                    <th>Click Rate</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  @for (c of stats.recentCampaigns; track c.id) {
                    <tr>
                      <td><strong>{{ c.name }}</strong></td>
                      <td>
                        <span class="status-dot {{ c.status?.toLowerCase() }}"></span>
                        <span class="badge" [class]="getStatusBadge(c.status)">{{ c.status }}</span>
                      </td>
                      <td>{{ c.sentCount | number }}</td>
                      <td>{{ c.openRate | number:'1.1-1' }}%</td>
                      <td>{{ c.clickRate | number:'1.1-1' }}%</td>
                      <td>
                        <a [routerLink]="['/campaigns', c.id, 'stats']" class="btn btn-sm btn-secondary">
                          <i class="fas fa-chart-bar"></i>
                        </a>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <div class="empty-state">
              <i class="fas fa-paper-plane"></i>
              <h3>No campaigns yet</h3>
              <p>Create your first campaign to get started</p>
              <br>
              <a routerLink="/campaigns/new" class="btn btn-primary">
                <i class="fas fa-plus"></i> Create Campaign
              </a>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class DashboardComponent implements OnInit {
  stats: DashboardStats | null = null;
  loading = true;
  Math = Math;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getDashboard().subscribe({
      next: (data) => { this.stats = data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  getRateClass(value: number, warn: number, good: number): string {
    if (value >= good) return 'good';
    if (value >= warn) return 'ok';
    return 'bad';
  }

  getBounceClass(value: number): string {
    if (value <= 2) return 'good';
    if (value <= 5) return 'ok';
    return 'bad';
  }

  getStatusBadge(status?: string): string {
    const map: Record<string, string> = {
      'COMPLETED': 'badge-success', 'SENDING': 'badge-primary',
      'PAUSED': 'badge-warning', 'DRAFT': 'badge-gray',
      'FAILED': 'badge-danger', 'CANCELLED': 'badge-gray', 'SCHEDULED': 'badge-info'
    };
    return map[status || ''] || 'badge-gray';
  }
}
