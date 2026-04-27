import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { Campaign } from '../../models/models';

@Component({
  selector: 'app-campaigns',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  styles: [`
    .campaign-card {
      background: white;
      border-radius: 10px;
      padding: 20px;
      box-shadow: var(--shadow);
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 12px;
      transition: box-shadow 0.15s;
      &:hover { box-shadow: var(--shadow-md); }
    }
    .campaign-icon {
      width: 44px; height: 44px;
      border-radius: 10px;
      background: #EFF6FF;
      color: var(--primary);
      display: flex; align-items: center; justify-content: center;
      font-size: 18px; flex-shrink: 0;
    }
    .campaign-info { flex: 1; min-width: 0; }
    .campaign-name { font-weight: 600; color: var(--gray-800); margin-bottom: 4px; }
    .campaign-meta { font-size: 12px; color: var(--gray-400); display:flex; gap:16px; }
    .campaign-stats {
      display: flex; gap: 24px;
      .metric { text-align: center;
        .val { font-size: 18px; font-weight: 700; color: var(--gray-800); }
        .lbl { font-size: 11px; color: var(--gray-400); text-transform: uppercase; letter-spacing: 0.5px; }
      }
    }
    .campaign-actions { display:flex; gap:6px; align-items:center; }
    .progress-info {
      display: flex; align-items: center; gap: 8px; font-size: 13px;
      .progress-bar { width: 100px; }
    }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">Campaigns</div>
          <div class="page-subtitle">{{ total }} campaigns total</div>
        </div>
        <div class="page-actions">
          <a routerLink="/campaigns/new" class="btn btn-primary">
            <i class="fas fa-plus"></i> New Campaign
          </a>
        </div>
      </div>

      @if (loading) {
        <div style="text-align:center;padding:60px"><div class="spinner spinner-dark"></div></div>
      } @else if (campaigns.length === 0) {
        <div class="empty-state">
          <i class="fas fa-paper-plane"></i>
          <h3>No campaigns yet</h3>
          <p>Create your first email campaign to start reaching your audience</p>
          <br>
          <a routerLink="/campaigns/new" class="btn btn-primary">
            <i class="fas fa-plus"></i> Create Campaign
          </a>
        </div>
      } @else {
        @for (campaign of campaigns; track campaign.id) {
          <div class="campaign-card">
            <div class="campaign-icon">
              <i class="fas fa-paper-plane"></i>
            </div>
            <div class="campaign-info">
              <div class="campaign-name">{{ campaign.name }}</div>
              <div class="campaign-meta">
                <span><i class="fas fa-envelope"></i> {{ campaign.subject }}</span>
                <span><i class="fas fa-calendar"></i> {{ campaign.createdAt | date:'MMM d, y' }}</span>
                @if (campaign.totalRecipients) {
                  <span><i class="fas fa-users"></i> {{ campaign.totalRecipients | number }} recipients</span>
                }
              </div>
              @if (campaign.status === 'SENDING' && campaign.totalRecipients) {
                <div class="progress-info" style="margin-top:8px">
                  <div class="progress-bar">
                    <div class="progress-fill"
                         [style.width.%]="(campaign.sentCount || 0) / campaign.totalRecipients * 100"></div>
                  </div>
                  <span>{{ campaign.sentCount }}/{{ campaign.totalRecipients }}</span>
                </div>
              }
            </div>

            <!-- Stats -->
            @if (campaign.status === 'COMPLETED' || campaign.status === 'SENDING') {
              <div class="campaign-stats">
                <div class="metric">
                  <div class="val">{{ campaign.sentCount | number }}</div>
                  <div class="lbl">Sent</div>
                </div>
                <div class="metric">
                  <div class="val">{{ campaign.openRate | number:'1.1-1' }}%</div>
                  <div class="lbl">Open</div>
                </div>
                <div class="metric">
                  <div class="val">{{ campaign.clickRate | number:'1.1-1' }}%</div>
                  <div class="lbl">Click</div>
                </div>
                <div class="metric">
                  <div class="val">{{ campaign.bounceRate | number:'1.1-1' }}%</div>
                  <div class="lbl">Bounce</div>
                </div>
              </div>
            }

            <!-- Status -->
            <span class="badge" [class]="getStatusBadge(campaign.status)">{{ campaign.status }}</span>

            <!-- Actions -->
            <div class="campaign-actions">
              @if (campaign.status === 'DRAFT' || campaign.status === 'PAUSED') {
                <button class="btn btn-sm btn-success" (click)="sendCampaign(campaign)"
                        [title]="campaign.status === 'PAUSED' ? 'Resume' : 'Send'">
                  <i class="fas" [class.fa-play]="campaign.status === 'PAUSED'"
                     [class.fa-paper-plane]="campaign.status === 'DRAFT'"></i>
                </button>
              }
              @if (campaign.status === 'SENDING') {
                <button class="btn btn-sm btn-warning" (click)="pauseCampaign(campaign)" title="Pause">
                  <i class="fas fa-pause"></i>
                </button>
                <button class="btn btn-sm btn-danger" (click)="cancelCampaign(campaign)" title="Cancel">
                  <i class="fas fa-stop"></i>
                </button>
              }
              @if (campaign.status === 'DRAFT') {
                <a [routerLink]="['/campaigns', campaign.id, 'edit']" class="btn btn-sm btn-secondary" title="Edit">
                  <i class="fas fa-pencil-alt"></i>
                </a>
              }
              <a [routerLink]="['/campaigns', campaign.id, 'stats']" class="btn btn-sm btn-secondary" title="Stats">
                <i class="fas fa-chart-bar"></i>
              </a>
              @if (campaign.status === 'DRAFT' || campaign.status === 'COMPLETED' || campaign.status === 'CANCELLED') {
                <button class="btn btn-icon" style="color:var(--danger)" (click)="deleteCampaign(campaign)" title="Delete">
                  <i class="fas fa-trash"></i>
                </button>
              }
            </div>
          </div>
        }

        <!-- Pagination -->
        @if (totalPages > 1) {
          <div class="pagination">
            <button (click)="changePage(currentPage - 1)" [disabled]="currentPage === 0">
              <i class="fas fa-chevron-left"></i>
            </button>
            @for (p of getPages(); track p) {
              <button (click)="changePage(p)" [class.active]="p === currentPage">{{ p + 1 }}</button>
            }
            <button (click)="changePage(currentPage + 1)" [disabled]="currentPage >= totalPages - 1">
              <i class="fas fa-chevron-right"></i>
            </button>
          </div>
        }
      }
    </div>
  `
})
export class CampaignsComponent implements OnInit {
  campaigns: Campaign[] = [];
  loading = false;
  total = 0;
  totalPages = 0;
  currentPage = 0;

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit(): void { this.loadCampaigns(); }

  loadCampaigns(): void {
    this.loading = true;
    this.api.getCampaigns(this.currentPage).subscribe({
      next: (data) => {
        this.campaigns = data.content;
        this.total = data.totalElements;
        this.totalPages = data.totalPages;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  sendCampaign(c: Campaign): void {
    if (c.status === 'PAUSED') {
      this.api.resumeCampaign(c.id!).subscribe(() => { this.toast.success('Campaign resumed'); this.loadCampaigns(); });
    } else {
      if (!confirm(`Send campaign "${c.name}" to all active contacts?`)) return;
      this.api.sendCampaign(c.id!).subscribe({
        next: () => { this.toast.success('Campaign started!'); this.loadCampaigns(); },
        error: (e) => this.toast.error('Failed to send campaign', e.error?.error || e.error?.message || 'Please check your campaign has content and an SMTP server configured.')
      });
    }
  }

  pauseCampaign(c: Campaign): void {
    this.api.pauseCampaign(c.id!).subscribe(() => { this.toast.warning('Campaign paused'); this.loadCampaigns(); });
  }

  cancelCampaign(c: Campaign): void {
    if (!confirm('Cancel this campaign?')) return;
    this.api.cancelCampaign(c.id!).subscribe(() => { this.toast.info('Campaign cancelled'); this.loadCampaigns(); });
  }

  deleteCampaign(c: Campaign): void {
    if (!confirm(`Delete campaign "${c.name}"?`)) return;
    this.api.deleteCampaign(c.id!).subscribe(() => { this.toast.success('Deleted'); this.loadCampaigns(); });
  }

  changePage(p: number): void { this.currentPage = p; this.loadCampaigns(); }
  getPages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  getStatusBadge(status: string): string {
    const map: Record<string, string> = {
      'COMPLETED': 'badge-success', 'SENDING': 'badge-primary',
      'PAUSED': 'badge-warning', 'DRAFT': 'badge-gray',
      'FAILED': 'badge-danger', 'CANCELLED': 'badge-gray', 'SCHEDULED': 'badge-info'
    };
    return map[status] || 'badge-gray';
  }
}
