import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { DashboardStats, Campaign } from '../../models/models';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, RouterLink],
  styles: [`
    .chart-placeholder {
      background:var(--gray-50); border:2px dashed var(--gray-200);
      border-radius:8px; height:200px;
      display:flex; flex-direction:column; align-items:center; justify-content:center;
      color:var(--gray-400);
      i { font-size:32px; margin-bottom:8px; }
    }
    .rate-badge {
      display:inline-flex; align-items:center; gap:4px;
      padding:2px 8px; border-radius:100px; font-size:11px; font-weight:700;
      &.good { background:#ECFDF5; color:var(--success); }
      &.warn { background:#FFFBEB; color:var(--warning); }
      &.bad { background:#FEF2F2; color:var(--danger); }
    }
    .two-col { display:grid; grid-template-columns:1fr 1fr; gap:20px; margin-bottom:20px; }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">Analytics</div>
          <div class="page-subtitle">Performance overview across all campaigns</div>
        </div>
      </div>

      @if (loading) {
        <div style="text-align:center;padding:60px"><div class="spinner spinner-dark"></div></div>
      } @else if (stats) {

        <!-- Top stats -->
        <div class="stats-grid" style="margin-bottom:24px">
          <div class="stat-card">
            <div class="stat-icon blue"><i class="fas fa-paper-plane"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalSent | number }}</div>
              <div class="stat-label">Total Emails Sent</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon green"><i class="fas fa-envelope-open"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalOpens | number }}</div>
              <div class="stat-label">Total Opens</div>
              <div class="stat-change" [class.positive]="stats.avgOpenRate >= 15" [class.negative]="stats.avgOpenRate < 15">
                Avg: {{ stats.avgOpenRate | number:'1.1-1' }}%
              </div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon purple"><i class="fas fa-mouse-pointer"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalClicks | number }}</div>
              <div class="stat-label">Total Clicks</div>
              <div class="stat-change" [class.positive]="stats.avgClickRate >= 2">
                Avg: {{ stats.avgClickRate | number:'1.1-1' }}%
              </div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon red"><i class="fas fa-exclamation-triangle"></i></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalBounces | number }}</div>
              <div class="stat-label">Total Bounces</div>
              <div class="stat-change" [class.negative]="stats.avgBounceRate >= 2">
                Avg: {{ stats.avgBounceRate | number:'1.1-1' }}%
              </div>
            </div>
          </div>
        </div>

        <div class="two-col">
          <!-- Rates card -->
          <div class="card">
            <div class="card-header"><h3>Average Rates</h3></div>
            <div style="display:flex;flex-direction:column;gap:16px">
              @for (metric of rateMetrics; track metric.label) {
                <div>
                  <div style="display:flex;justify-content:space-between;margin-bottom:6px">
                    <span style="font-size:14px;font-weight:500">{{ metric.label }}</span>
                    <div style="display:flex;align-items:center;gap:8px">
                      <span style="font-size:16px;font-weight:700">{{ metric.value | number:'1.1-1' }}%</span>
                      <span class="rate-badge" [class]="metric.class">{{ metric.status }}</span>
                    </div>
                  </div>
                  <div class="progress-bar">
                    <div class="progress-fill" [class]="metric.barClass" [style.width.%]="metric.barWidth"></div>
                  </div>
                  <div style="font-size:11px;color:var(--gray-400);margin-top:3px">Benchmark: {{ metric.benchmark }}</div>
                </div>
              }
            </div>
          </div>

          <!-- Top campaigns -->
          <div class="card">
            <div class="card-header"><h3>Top Campaigns by Opens</h3></div>
            @if (stats.recentCampaigns?.length) {
              @for (c of topByOpen; track c.id) {
                <div style="display:flex;align-items:center;gap:12px;margin-bottom:12px">
                  <div style="flex:1;min-width:0">
                    <div style="font-weight:500;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                      {{ c.name }}
                    </div>
                    <div class="progress-bar" style="margin-top:5px">
                      <div class="progress-fill success" [style.width.%]="c.openRate || 0"></div>
                    </div>
                  </div>
                  <span style="font-size:15px;font-weight:700;min-width:48px;text-align:right">
                    {{ c.openRate | number:'1.1-1' }}%
                  </span>
                </div>
              }
            } @else {
              <div class="empty-state" style="padding:30px">
                <i class="fas fa-chart-bar"></i>
                <p>No campaign data yet</p>
              </div>
            }
          </div>
        </div>

        <!-- Campaign table -->
        <div class="card">
          <div class="card-header">
            <h3>All Campaigns Performance</h3>
          </div>
          <div class="table-container">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Campaign</th>
                  <th>Sent</th>
                  <th>Opens</th>
                  <th>Clicks</th>
                  <th>Bounces</th>
                  <th>Open Rate</th>
                  <th>Click Rate</th>
                  <th>Bounce Rate</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (c of stats.recentCampaigns; track c.id) {
                  <tr>
                    <td><strong>{{ c.name }}</strong></td>
                    <td>{{ c.sentCount | number }}</td>
                    <td>{{ c.openCount || 0 | number }}</td>
                    <td>{{ c.clickCount || 0 | number }}</td>
                    <td>{{ c.bounceCount || 0 | number }}</td>
                    <td>
                      <span [style.color]="(c.openRate || 0) >= 15 ? 'var(--success)' : 'var(--warning)'">
                        {{ c.openRate | number:'1.1-1' }}%
                      </span>
                    </td>
                    <td>
                      <span [style.color]="(c.clickRate || 0) >= 2 ? 'var(--success)' : 'var(--warning)'">
                        {{ c.clickRate | number:'1.1-1' }}%
                      </span>
                    </td>
                    <td>
                      <span [style.color]="(c.bounceRate || 0) < 2 ? 'var(--success)' : 'var(--danger)'">
                        {{ c.bounceRate | number:'1.1-1' }}%
                      </span>
                    </td>
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
        </div>
      }
    </div>
  `
})
export class AnalyticsComponent implements OnInit {
  stats: DashboardStats | null = null;
  loading = true;

  rateMetrics: any[] = [];
  topByOpen: Campaign[] = [];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getDashboard().subscribe({
      next: (data) => {
        this.stats = data;
        this.loading = false;
        this.buildMetrics(data);
        this.topByOpen = [...(data.recentCampaigns || [])]
          .sort((a, b) => (b.openRate || 0) - (a.openRate || 0))
          .slice(0, 5);
      },
      error: () => this.loading = false
    });
  }

  buildMetrics(stats: DashboardStats): void {
    this.rateMetrics = [
      {
        label: 'Open Rate',
        value: stats.avgOpenRate,
        benchmark: '15–25%',
        status: stats.avgOpenRate >= 25 ? 'Excellent' : stats.avgOpenRate >= 15 ? 'Good' : 'Below Avg',
        class: stats.avgOpenRate >= 15 ? 'good' : stats.avgOpenRate >= 10 ? 'warn' : 'bad',
        barClass: stats.avgOpenRate >= 15 ? 'success' : stats.avgOpenRate >= 10 ? 'warning' : 'danger',
        barWidth: Math.min(stats.avgOpenRate * 2, 100)
      },
      {
        label: 'Click Rate',
        value: stats.avgClickRate,
        benchmark: '2–5%',
        status: stats.avgClickRate >= 5 ? 'Excellent' : stats.avgClickRate >= 2 ? 'Good' : 'Below Avg',
        class: stats.avgClickRate >= 2 ? 'good' : 'warn',
        barClass: stats.avgClickRate >= 2 ? 'success' : 'warning',
        barWidth: Math.min(stats.avgClickRate * 10, 100)
      },
      {
        label: 'Bounce Rate',
        value: stats.avgBounceRate,
        benchmark: '<2%',
        status: stats.avgBounceRate < 2 ? 'Good' : stats.avgBounceRate < 5 ? 'Warning' : 'Critical',
        class: stats.avgBounceRate < 2 ? 'good' : stats.avgBounceRate < 5 ? 'warn' : 'bad',
        barClass: stats.avgBounceRate < 2 ? 'success' : stats.avgBounceRate < 5 ? 'warning' : 'danger',
        barWidth: Math.min(stats.avgBounceRate * 10, 100)
      }
    ];
  }
}
