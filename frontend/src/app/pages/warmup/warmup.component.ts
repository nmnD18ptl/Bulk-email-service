import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { WarmupPlan, WarmupScheduleDay, SmtpConfig } from '../../models/models';

@Component({
  selector: 'app-warmup',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    .warmup-cards { display:grid; grid-template-columns:repeat(auto-fill,minmax(340px,1fr)); gap:20px; }
    .warmup-card {
      background:white; border-radius:10px; padding:20px; box-shadow:var(--shadow);
      .warmup-header { display:flex; align-items:center; gap:12px; margin-bottom:16px;
        .warmup-icon { width:44px; height:44px; border-radius:10px; background:#FEF3C7;
          color:#D97706; display:flex; align-items:center; justify-content:center; font-size:20px; }
      }
      .stage-progress { margin:16px 0;
        .stage-info { display:flex; justify-content:space-between; font-size:13px; margin-bottom:6px;
          color:var(--gray-500); }
      }
    }
    .schedule-grid {
      display:grid; grid-template-columns:repeat(7,1fr); gap:6px; margin-top:16px;
    }
    .day-cell {
      background:white; border-radius:6px; padding:8px; text-align:center; border:1px solid var(--gray-200);
      .day-num { font-size:11px; color:var(--gray-400); }
      .day-vol { font-size:13px; font-weight:700; color:var(--gray-700); }
      .day-date { font-size:10px; color:var(--gray-400); margin-top:2px; }
      &.past { background:var(--gray-50); opacity:0.6; }
      &.today { border-color:var(--primary); background:#EFF6FF; .day-vol { color:var(--primary); } }
      &.future { opacity:0.8; }
    }
    .info-box {
      background:#FFFBEB; border:1px solid #FDE68A; border-radius:8px; padding:14px;
      font-size:13px; color:#92400E; display:flex; gap:10px;
      i { margin-top:1px; }
    }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">IP Warming</div>
          <div class="page-subtitle">Gradually build sender reputation for better deliverability</div>
        </div>
        <button class="btn btn-primary" (click)="showNewPlan = true">
          <i class="fas fa-fire"></i> New Warmup Plan
        </button>
      </div>

      <div class="info-box" style="margin-bottom:24px">
        <i class="fas fa-lightbulb"></i>
        <div>
          <strong>What is IP Warming?</strong> When using a new IP or domain, start by sending small volumes and
          gradually increase. This builds trust with email providers (Gmail, Outlook) and prevents your
          emails from being marked as spam. Typical warmup: 50 → 5,000 emails over 14 days.
        </div>
      </div>

      @if (loading) {
        <div style="text-align:center;padding:60px"><div class="spinner spinner-dark"></div></div>
      } @else if (warmupPlans.length === 0) {
        <div class="empty-state card">
          <i class="fas fa-fire"></i>
          <h3>No warmup plans</h3>
          <p>Create a warmup plan to build sender reputation for a new IP or domain</p>
          <br>
          <button class="btn btn-primary" (click)="showNewPlan = true">
            <i class="fas fa-plus"></i> Create Warmup Plan
          </button>
        </div>
      } @else {
        <div class="warmup-cards">
          @for (plan of warmupPlans; track plan.id) {
            <div class="warmup-card">
              <div class="warmup-header">
                <div class="warmup-icon"><i class="fas fa-fire"></i></div>
                <div>
                  <div style="font-weight:600">{{ plan.name }}</div>
                  <div style="font-size:12px;color:var(--gray-400)">
                    Target: {{ plan.targetDailyVolume | number }} emails/day &bull;
                    {{ plan.totalStages }} days
                  </div>
                </div>
                <span class="badge" style="margin-left:auto"
                      [class.badge-success]="plan.status === 'ACTIVE'"
                      [class.badge-warning]="plan.status === 'PAUSED'"
                      [class.badge-gray]="plan.status === 'NOT_STARTED'"
                      [class.badge-primary]="plan.status === 'COMPLETED'">
                  {{ plan.status }}
                </span>
              </div>

              <div class="stage-progress">
                <div class="stage-info">
                  <span>Stage {{ plan.currentStage }} of {{ plan.totalStages }}</span>
                  <span>Today: {{ plan.currentDayVolume | number }} emails</span>
                </div>
                <div class="progress-bar">
                  <div class="progress-fill success"
                       [style.width.%]="(plan.currentStage || 0) / (plan.totalStages || 14) * 100"></div>
                </div>
              </div>

              @if (plan.startDate) {
                <div style="font-size:12px;color:var(--gray-400);margin-bottom:12px">
                  <i class="fas fa-calendar"></i> Started: {{ plan.startDate | date:'MMM d, y' }}
                  @if (plan.estimatedCompletionDate) {
                    &bull; Est. complete: {{ plan.estimatedCompletionDate | date:'MMM d, y' }}
                  }
                </div>
              }

              <div style="display:flex;gap:8px">
                @if (plan.status === 'NOT_STARTED') {
                  <button class="btn btn-sm btn-success" (click)="startPlan(plan)">
                    <i class="fas fa-play"></i> Start
                  </button>
                }
                @if (plan.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-warning" (click)="pausePlan(plan)">
                    <i class="fas fa-pause"></i> Pause
                  </button>
                }
                @if (plan.status === 'PAUSED') {
                  <button class="btn btn-sm btn-success" (click)="resumePlan(plan)">
                    <i class="fas fa-play"></i> Resume
                  </button>
                }
                <button class="btn btn-sm btn-secondary" (click)="viewSchedule(plan)">
                  <i class="fas fa-calendar"></i> Schedule
                </button>
                <button class="btn btn-icon" style="color:var(--danger)" (click)="deletePlan(plan)">
                  <i class="fas fa-trash"></i>
                </button>
              </div>
            </div>
          }
        </div>
      }

      <!-- Preview schedule generator -->
      <div class="card" style="margin-top:24px">
        <div class="card-header"><h3>Generate Warmup Schedule Preview</h3></div>
        <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
          <div class="form-group" style="margin-bottom:0;display:flex;align-items:center;gap:10px">
            <label>Target Volume:</label>
            <input type="number" class="form-control" style="width:120px" [(ngModel)]="previewVolume" min="100" step="500">
          </div>
          <button class="btn btn-primary" (click)="generatePreview()">
            <i class="fas fa-calendar-alt"></i> Preview Schedule
          </button>
        </div>
        @if (schedulePreview.length) {
          <div class="schedule-grid" style="margin-top:16px">
            @for (day of schedulePreview; track day.day) {
              <div class="day-cell">
                <div class="day-num">Day {{ day.day }}</div>
                <div class="day-vol">{{ day.volume | number }}</div>
                <div class="day-date">{{ day.date | date:'MMM d' }}</div>
              </div>
            }
          </div>
        }
      </div>
    </div>

    <!-- New Plan Modal -->
    @if (showNewPlan) {
      <div class="modal-overlay" (click)="showNewPlan = false">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>New Warmup Plan</h3>
            <button class="close-btn" (click)="showNewPlan = false"><i class="fas fa-times"></i></button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label>Plan Name *</label>
              <input class="form-control" [(ngModel)]="newPlan.name" placeholder="Gmail Warmup - April 2024">
            </div>
            <div class="form-group">
              <label>SMTP Configuration</label>
              <select class="form-control" [(ngModel)]="selectedSmtpId">
                <option value="">Select SMTP server</option>
                @for (s of smtpConfigs; track s.id) {
                  <option [value]="s.id">{{ s.name }}</option>
                }
              </select>
            </div>
            <div class="form-group">
              <label>Target Daily Volume</label>
              <input type="number" class="form-control" [(ngModel)]="newPlan.targetDailyVolume"
                     placeholder="5000" min="100">
            </div>
            <div class="form-group">
              <label>Bounce Rate Threshold (%)</label>
              <input type="number" class="form-control" [(ngModel)]="newPlan.bounceRateThreshold"
                     placeholder="2.0" step="0.5">
              <small style="color:var(--gray-400)">Auto-pause if bounce rate exceeds this</small>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showNewPlan = false">Cancel</button>
            <button class="btn btn-primary" (click)="createPlan()" [disabled]="creating">
              <span *ngIf="creating" class="spinner"></span> Create Plan
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Schedule Modal -->
    @if (selectedPlanSchedule) {
      <div class="modal-overlay" (click)="selectedPlanSchedule = null">
        <div class="modal" style="max-width:700px" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>Warmup Schedule</h3>
            <button class="close-btn" (click)="selectedPlanSchedule = null"><i class="fas fa-times"></i></button>
          </div>
          <div class="modal-body">
            <div class="schedule-grid">
              @for (day of selectedPlanSchedule; track day.day) {
                <div class="day-cell">
                  <div class="day-num">Day {{ day.day }}</div>
                  <div class="day-vol">{{ day.volume | number }}</div>
                  <div class="day-date">{{ day.date | date:'MMM d' }}</div>
                </div>
              }
            </div>
          </div>
        </div>
      </div>
    }
  `
})
export class WarmupComponent implements OnInit {
  warmupPlans: WarmupPlan[] = [];
  loading = true;
  showNewPlan = false;
  creating = false;
  smtpConfigs: SmtpConfig[] = [];
  selectedSmtpId = '';
  schedulePreview: WarmupScheduleDay[] = [];
  previewVolume = 5000;
  selectedPlanSchedule: WarmupScheduleDay[] | null = null;

  newPlan: Partial<WarmupPlan> = {
    name: '',
    targetDailyVolume: 5000,
    bounceRateThreshold: 2.0
  };

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit(): void {
    this.loadPlans();
    this.api.getSmtpConfigs().subscribe(s => this.smtpConfigs = s);
  }

  loadPlans(): void {
    this.loading = true;
    this.api.getWarmupPlans().subscribe({
      next: (p) => { this.warmupPlans = p; this.loading = false; },
      error: () => this.loading = false
    });
  }

  createPlan(): void {
    if (!this.newPlan.name) { this.toast.error('Plan name is required'); return; }
    this.creating = true;
    const data: any = { ...this.newPlan };
    if (this.selectedSmtpId) data.smtpConfig = { id: +this.selectedSmtpId };
    this.api.createWarmupPlan(data).subscribe({
      next: () => {
        this.toast.success('Warmup plan created');
        this.showNewPlan = false;
        this.creating = false;
        this.newPlan = { name: '', targetDailyVolume: 5000, bounceRateThreshold: 2.0 };
        this.loadPlans();
      },
      error: () => { this.creating = false; this.toast.error('Failed to create'); }
    });
  }

  startPlan(plan: WarmupPlan): void {
    this.api.startWarmup(plan.id!).subscribe(() => { this.toast.success('Warmup started'); this.loadPlans(); });
  }

  pausePlan(plan: WarmupPlan): void {
    this.api.pauseWarmup(plan.id!).subscribe(() => { this.toast.warning('Warmup paused'); this.loadPlans(); });
  }

  resumePlan(plan: WarmupPlan): void {
    this.api.resumeWarmup(plan.id!).subscribe(() => { this.toast.success('Warmup resumed'); this.loadPlans(); });
  }

  deletePlan(plan: WarmupPlan): void {
    if (!confirm(`Delete "${plan.name}"?`)) return;
    this.api.deleteWarmupPlan(plan.id!).subscribe(() => { this.toast.success('Deleted'); this.loadPlans(); });
  }

  viewSchedule(plan: WarmupPlan): void {
    this.api.getWarmupSchedule(plan.targetDailyVolume || 5000).subscribe(s => {
      this.selectedPlanSchedule = s;
    });
  }

  generatePreview(): void {
    this.api.getWarmupSchedule(this.previewVolume).subscribe(s => this.schedulePreview = s);
  }
}
