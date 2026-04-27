import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { Campaign, SmtpConfig, Template, SpamAnalysis, Tag } from '../../models/models';

type BuilderStep = 'details' | 'content' | 'audience' | 'settings' | 'review';

@Component({
  selector: 'app-campaign-builder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    .builder-layout { display:flex; gap:24px; }
    .step-sidebar {
      width: 220px; flex-shrink: 0;
      .step-item {
        display:flex; align-items:center; gap:10px;
        padding:12px 14px; border-radius:8px;
        cursor:pointer; margin-bottom:4px; color:var(--gray-500);
        font-size:14px; font-weight:500; transition:all 0.15s;
        &:hover { background:var(--gray-100); color:var(--gray-700); }
        &.active { background:#EFF6FF; color:var(--primary); }
        &.done { color:var(--success); }
        .step-num { width:24px; height:24px; border-radius:50%; background:var(--gray-200);
          display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:700; flex-shrink:0; }
        &.active .step-num { background:var(--primary); color:white; }
        &.done .step-num { background:var(--success); color:white; }
      }
    }
    .builder-content { flex:1; min-width:0; }
    .step-card { background:white; border-radius:10px; padding:24px; box-shadow:var(--shadow); }
    .step-actions { display:flex; gap:10px; justify-content:flex-end; margin-top:24px; }
    .html-editor {
      border:1px solid var(--gray-300); border-radius:6px;
      font-family:monospace; font-size:13px;
      min-height:300px; resize:vertical; width:100%;
      padding:12px; outline:none; line-height:1.5;
      &:focus { border-color:var(--primary); }
    }
    .email-preview {
      border:1px solid var(--gray-300); border-radius:6px;
      overflow:hidden;
      iframe { width:100%; height:500px; border:none; }
    }
    .spam-score-bar {
      height:10px; border-radius:100px; overflow:hidden; background:var(--gray-200);
      margin:8px 0;
      .fill { height:100%; border-radius:100px; transition:width 0.5s;
        &.good { background:var(--success); }
        &.warning { background:var(--warning); }
        &.poor { background:var(--danger); }
      }
    }
    .spam-issue {
      display:flex; align-items:flex-start; gap:10px;
      padding:10px; border-radius:6px; margin-bottom:8px;
      font-size:13px;
      &.CRITICAL { background:#FEF2F2; color:#991B1B; }
      &.HIGH { background:#FFFBEB; color:#92400E; }
      &.MEDIUM { background:#FFFBEB; color:#92400E; }
      &.LOW { background:#F0FDF4; color:#065F46; }
    }
    .merge-tags { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
    .merge-tag {
      padding:3px 10px; background:#EFF6FF; color:var(--primary);
      border-radius:100px; font-size:12px; font-family:monospace;
      cursor:pointer; transition:background 0.15s;
      &:hover { background:var(--primary); color:white; }
    }
    .tab-bar { display:flex; gap:2px; border-bottom:2px solid var(--gray-200); margin-bottom:16px; }
    .tab { padding:8px 16px; cursor:pointer; font-size:14px; font-weight:500; color:var(--gray-500);
      border-bottom:2px solid transparent; margin-bottom:-2px; transition:all 0.15s;
      &.active { color:var(--primary); border-color:var(--primary); }
      &:hover:not(.active) { color:var(--gray-700); }
    }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">{{ campaignId ? 'Edit' : 'New' }} Campaign</div>
          <div class="page-subtitle">Build your email campaign step by step</div>
        </div>
        <button class="btn btn-secondary" (click)="router.navigate(['/campaigns'])">
          <i class="fas fa-arrow-left"></i> Back
        </button>
      </div>

      <div class="builder-layout">
        <!-- Step sidebar -->
        <div class="step-sidebar">
          @for (step of steps; track step.key) {
            <div class="step-item" [class.active]="currentStep === step.key"
                 [class.done]="isStepDone(step.key)" (click)="goToStep(step.key)">
              <div class="step-num">
                @if (isStepDone(step.key)) { <i class="fas fa-check" style="font-size:11px"></i> }
                @else { {{ step.num }} }
              </div>
              {{ step.label }}
            </div>
          }
        </div>

        <!-- Step content -->
        <div class="builder-content">
          <!-- Step 1: Details -->
          @if (currentStep === 'details') {
            <div class="step-card">
              <h3 style="margin-bottom:20px;font-size:17px">Campaign Details</h3>
              <div class="form-group">
                <label>Campaign Name *</label>
                <input class="form-control" [(ngModel)]="campaign.name" placeholder="e.g., June Newsletter 2024">
              </div>
              <div class="form-group">
                <label>Subject Line *</label>
                <input class="form-control" [(ngModel)]="campaign.subject"
                       placeholder="Use merge tags: {{'{{'}}FirstName{{'}}'}}, {{'{{'}}Company{{'}}'}}">
                @if (campaign.subject) {
                  <small style="color:var(--gray-400);font-size:12px">
                    {{ campaign.subject.length }}/150 characters
                    @if (campaign.subject.length > 60) {
                      <span style="color:var(--warning)"> (Consider keeping under 60)</span>
                    }
                  </small>
                }
              </div>
              <div class="form-group">
                <label>Preview Text</label>
                <input class="form-control" [(ngModel)]="campaign.previewText"
                       placeholder="Short preview shown in inbox (90-130 characters recommended)">
              </div>
              <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
                <div class="form-group">
                  <label>From Name *</label>
                  <input class="form-control" [(ngModel)]="campaign.fromName" placeholder="Your Name or Company">
                </div>
                <div class="form-group">
                  <label>From Email *</label>
                  <input class="form-control" [(ngModel)]="campaign.fromEmail" placeholder="you@yourdomain.com">
                </div>
              </div>
              <div class="form-group">
                <label>Reply-To Email</label>
                <input class="form-control" [(ngModel)]="campaign.replyToEmail" placeholder="Optional">
              </div>
              <div class="step-actions">
                <button class="btn btn-primary" (click)="goToStep('content')">
                  Next: Content <i class="fas fa-arrow-right"></i>
                </button>
              </div>
            </div>
          }

          <!-- Step 2: Content -->
          @if (currentStep === 'content') {
            <div class="step-card">
              <h3 style="margin-bottom:20px;font-size:17px">Email Content</h3>

              <!-- Template picker -->
              @if (templates.length) {
                <div class="form-group">
                  <label>Start from a template</label>
                  <select class="form-control" (change)="loadTemplate($event)">
                    <option value="">-- Select template --</option>
                    @for (t of templates; track t.id) {
                      <option [value]="t.id">{{ t.name }} ({{ t.category }})</option>
                    }
                  </select>
                </div>
              }

              <!-- Tab bar -->
              <div class="tab-bar">
                <div class="tab" [class.active]="contentTab === 'html'" (click)="contentTab = 'html'">
                  <i class="fas fa-code"></i> HTML Editor
                </div>
                <div class="tab" [class.active]="contentTab === 'preview'" (click)="showPreview()">
                  <i class="fas fa-eye"></i> Preview
                </div>
                <div class="tab" [class.active]="contentTab === 'text'" (click)="contentTab = 'text'">
                  <i class="fas fa-align-left"></i> Plain Text
                </div>
                <div class="tab" [class.active]="contentTab === 'spam'" (click)="runSpamCheck()">
                  <i class="fas fa-shield-alt"></i> Spam Check
                </div>
              </div>

              @if (contentTab === 'html') {
                <!-- Merge tags helper -->
                <div class="form-group">
                  <label>Available Merge Tags (click to copy)</label>
                  <div class="merge-tags">
                    @for (tag of mergeTags; track tag) {
                      <span class="merge-tag" (click)="copyMergeTag(tag)">{{ tag }}</span>
                    }
                  </div>
                </div>
                <div class="form-group">
                  <label>HTML Content *</label>
                  <textarea class="html-editor" [(ngModel)]="campaign.htmlContent"
                            placeholder="Enter your HTML email content here...
Use merge tags like {{'{{'}}FirstName{{'}}'}}, {{'{{'}}UnsubscribeLink{{'}}'}} etc."></textarea>
                </div>
              }

              @if (contentTab === 'preview') {
                <div class="email-preview">
                  <iframe [srcdoc]="campaign.htmlContent || '<p>No content yet</p>'"></iframe>
                </div>
              }

              @if (contentTab === 'text') {
                <div class="form-group">
                  <label>Plain Text Version</label>
                  <textarea class="form-control" style="min-height:300px;font-family:monospace"
                            [(ngModel)]="campaign.textContent"
                            placeholder="Plain text version for email clients that don't support HTML"></textarea>
                </div>
              }

              @if (contentTab === 'spam') {
                @if (spamLoading) {
                  <div style="text-align:center;padding:40px"><div class="spinner spinner-dark"></div></div>
                } @else if (spamResult) {
                  <div style="margin-bottom:16px">
                    <div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">
                      <span style="font-size:14px;font-weight:600">Spam Score:</span>
                      <span style="font-size:24px;font-weight:800"
                            [style.color]="spamResult.rating === 'GOOD' ? 'var(--success)' : spamResult.rating === 'WARNING' ? 'var(--warning)' : 'var(--danger)'">
                        {{ spamResult.score }}/100
                      </span>
                      <span class="badge"
                            [class.badge-success]="spamResult.rating === 'GOOD'"
                            [class.badge-warning]="spamResult.rating === 'WARNING'"
                            [class.badge-danger]="spamResult.rating === 'POOR'">
                        {{ spamResult.rating }}
                      </span>
                    </div>
                    <div class="spam-score-bar">
                      <div class="fill" [class]="spamResult.rating.toLowerCase()"
                           [style.width.%]="spamResult.score"></div>
                    </div>
                  </div>
                  @if (spamResult.issues.length === 0) {
                    <div class="alert alert-success"><i class="fas fa-check-circle"></i> No spam issues detected!</div>
                  }
                  @for (issue of spamResult.issues; track issue.code) {
                    <div class="spam-issue {{ issue.severity }}">
                      <i class="fas" [class.fa-times-circle]="issue.severity === 'CRITICAL' || issue.severity === 'HIGH'"
                         [class.fa-exclamation-triangle]="issue.severity === 'MEDIUM'"
                         [class.fa-info-circle]="issue.severity === 'LOW'"></i>
                      <div>
                        <strong>{{ issue.message }}</strong>
                        <div style="margin-top:4px;opacity:0.8">{{ issue.recommendation }}</div>
                      </div>
                    </div>
                  }
                } @else {
                  <div class="empty-state" style="padding:30px">
                    <i class="fas fa-shield-alt"></i>
                    <h3>Run Spam Analysis</h3>
                    <p>Check your email for common spam triggers before sending</p>
                    <br>
                    <button class="btn btn-primary" (click)="runSpamCheck()">
                      <i class="fas fa-search"></i> Analyze Now
                    </button>
                  </div>
                }
              }

              <div class="step-actions">
                <button class="btn btn-secondary" (click)="goToStep('details')">
                  <i class="fas fa-arrow-left"></i> Back
                </button>
                <button class="btn btn-primary" (click)="goToStep('audience')">
                  Next: Audience <i class="fas fa-arrow-right"></i>
                </button>
              </div>
            </div>
          }

          <!-- Step 3: Audience -->
          @if (currentStep === 'audience') {
            <div class="step-card">
              <h3 style="margin-bottom:20px;font-size:17px">Select Audience</h3>
              <div class="form-group">
                <label>
                  <input type="checkbox" [(ngModel)]="campaign.sendToAll" style="margin-right:8px">
                  Send to all active contacts
                </label>
              </div>
              @if (!campaign.sendToAll && availableTags.length) {
                <div class="form-group">
                  <label>Filter by tags</label>
                  <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px">
                    @for (tag of availableTags; track tag.id) {
                      <label style="display:flex;align-items:center;gap:6px;cursor:pointer;
                        padding:6px 12px;border-radius:100px;border:1px solid var(--gray-300);
                        background:white;font-size:13px">
                        <input type="checkbox" [checked]="isTagSelected(tag.id!)"
                               (change)="toggleTagFilter(tag.id!)">
                        <span [style.color]="tag.color || '#3B82F6'">{{ tag.name }}</span>
                        <span style="color:var(--gray-400)">({{ tag.contactCount }})</span>
                      </label>
                    }
                  </div>
                </div>
              }

              <div class="step-actions">
                <button class="btn btn-secondary" (click)="goToStep('content')">
                  <i class="fas fa-arrow-left"></i> Back
                </button>
                <button class="btn btn-primary" (click)="goToStep('settings')">
                  Next: Settings <i class="fas fa-arrow-right"></i>
                </button>
              </div>
            </div>
          }

          <!-- Step 4: Settings -->
          @if (currentStep === 'settings') {
            <div class="step-card">
              <h3 style="margin-bottom:20px;font-size:17px">Sending Settings</h3>

              <div class="form-group">
                <label>SMTP Configuration</label>
                <select class="form-control" [(ngModel)]="selectedSmtpId">
                  <option value="">Use default SMTP</option>
                  @for (smtp of smtpConfigs; track smtp.id) {
                    <option [value]="smtp.id">{{ smtp.name }} ({{ smtp.host }})</option>
                  }
                </select>
              </div>

              <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
                <div class="form-group">
                  <label>Batch Size</label>
                  <input type="number" class="form-control" [(ngModel)]="campaign.batchSize"
                         min="1" max="500" placeholder="100">
                  <small style="color:var(--gray-400)">Emails per batch (recommended: 50-200)</small>
                </div>
                <div class="form-group">
                  <label>Delay Between Batches (seconds)</label>
                  <input type="number" class="form-control" [(ngModel)]="campaign.batchDelaySeconds"
                         min="10" placeholder="60">
                  <small style="color:var(--gray-400)">Pause between batches (recommended: 60-300)</small>
                </div>
                <div class="form-group">
                  <label>Inter-Email Delay (ms)</label>
                  <input type="number" class="form-control" [(ngModel)]="campaign.interEmailDelayMs"
                         min="0" max="5000" placeholder="200">
                  <small style="color:var(--gray-400)">Delay between each email (recommended: 100-500ms)</small>
                </div>
                <div class="form-group">
                  <label>Max Retries</label>
                  <input type="number" class="form-control" [(ngModel)]="campaign.maxRetries"
                         min="0" max="5" placeholder="3">
                </div>
              </div>

              <div class="form-group">
                <label>Physical Address (CAN-SPAM Required)</label>
                <input class="form-control" [(ngModel)]="campaign.physicalAddress"
                       placeholder="123 Main St, City, State ZIP, Country">
              </div>

              <div style="display:flex;gap:20px">
                <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
                  <input type="checkbox" [(ngModel)]="campaign.trackOpens">
                  <span>Track Opens</span>
                </label>
                <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
                  <input type="checkbox" [(ngModel)]="campaign.trackClicks">
                  <span>Track Clicks</span>
                </label>
              </div>

              <div class="step-actions">
                <button class="btn btn-secondary" (click)="goToStep('audience')">
                  <i class="fas fa-arrow-left"></i> Back
                </button>
                <button class="btn btn-primary" (click)="goToStep('review')">
                  Review <i class="fas fa-arrow-right"></i>
                </button>
              </div>
            </div>
          }

          <!-- Step 5: Review -->
          @if (currentStep === 'review') {
            <div class="step-card">
              <h3 style="margin-bottom:20px;font-size:17px">Review & Send</h3>

              <div style="display:grid;gap:12px;margin-bottom:24px">
                <div style="display:flex;justify-content:space-between;padding:12px;background:var(--gray-50);border-radius:8px">
                  <span style="color:var(--gray-500)">Campaign Name</span>
                  <strong>{{ campaign.name }}</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:12px;background:var(--gray-50);border-radius:8px">
                  <span style="color:var(--gray-500)">Subject</span>
                  <strong>{{ campaign.subject }}</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:12px;background:var(--gray-50);border-radius:8px">
                  <span style="color:var(--gray-500)">From</span>
                  <strong>{{ campaign.fromName }} &lt;{{ campaign.fromEmail }}&gt;</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:12px;background:var(--gray-50);border-radius:8px">
                  <span style="color:var(--gray-500)">Audience</span>
                  <strong>{{ campaign.sendToAll ? 'All active contacts' : selectedTagNames }}</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:12px;background:var(--gray-50);border-radius:8px">
                  <span style="color:var(--gray-500)">Batch Settings</span>
                  <strong>{{ campaign.batchSize }} emails / {{ campaign.batchDelaySeconds }}s delay</strong>
                </div>
                <div style="display:flex;justify-content:space-between;padding:12px;background:var(--gray-50);border-radius:8px">
                  <span style="color:var(--gray-500)">Tracking</span>
                  <strong>Opens: {{ campaign.trackOpens ? '✓' : '✗' }}, Clicks: {{ campaign.trackClicks ? '✓' : '✗' }}</strong>
                </div>
              </div>

              <!-- Test email -->
              <div style="background:#EFF6FF;border-radius:8px;padding:16px;margin-bottom:20px">
                <label style="font-weight:600;color:var(--primary);margin-bottom:10px;display:block">
                  <i class="fas fa-flask"></i> Send Test Email
                </label>
                <div style="display:flex;gap:10px">
                  <input class="form-control" [(ngModel)]="testEmail" placeholder="test@email.com">
                  <button class="btn btn-primary" (click)="sendTest()" [disabled]="!testEmail || testSending">
                    {{ testSending ? 'Sending...' : 'Send Test' }}
                  </button>
                </div>
              </div>

              <div class="step-actions">
                <button class="btn btn-secondary" (click)="goToStep('settings')">
                  <i class="fas fa-arrow-left"></i> Back
                </button>
                <button class="btn btn-secondary" (click)="saveDraft()" [disabled]="saving">
                  <span *ngIf="saving" class="spinner spinner-dark"></span>
                  Save as Draft
                </button>
                <button class="btn btn-success" (click)="saveAndSend()" [disabled]="saving">
                  <i class="fas fa-paper-plane"></i> Save & Send Now
                </button>
              </div>
            </div>
          }
        </div>
      </div>
    </div>
  `
})
export class CampaignBuilderComponent implements OnInit {
  campaignId: number | null = null;
  currentStep: BuilderStep = 'details';
  campaign: Partial<Campaign> = {
    name: '', subject: '', sendToAll: true,
    batchSize: 100, batchDelaySeconds: 60, interEmailDelayMs: 200, maxRetries: 3,
    trackOpens: true, trackClicks: true, tagFilters: []
  };

  steps = [
    { key: 'details' as BuilderStep, num: 1, label: 'Details' },
    { key: 'content' as BuilderStep, num: 2, label: 'Content' },
    { key: 'audience' as BuilderStep, num: 3, label: 'Audience' },
    { key: 'settings' as BuilderStep, num: 4, label: 'Settings' },
    { key: 'review' as BuilderStep, num: 5, label: 'Review' }
  ];

  mergeTags = ['{{FirstName}}', '{{LastName}}', '{{FullName}}', '{{Email}}', '{{Company}}',
    '{{Country}}', '{{Phone}}', '{{CustomField1}}', '{{CustomField2}}', '{{CustomField3}}',
    '{{UnsubscribeLink}}', '{{CurrentYear}}'];

  contentTab: 'html' | 'preview' | 'text' | 'spam' = 'html';
  spamResult: SpamAnalysis | null = null;
  spamLoading = false;

  templates: Template[] = [];
  smtpConfigs: SmtpConfig[] = [];
  availableTags: Tag[] = [];
  selectedSmtpId = '';
  testEmail = '';
  testSending = false;
  saving = false;

  private completedSteps = new Set<BuilderStep>();

  constructor(
    private api: ApiService,
    private toast: ToastService,
    public router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.params['id'] ? +this.route.snapshot.params['id'] : null;
    this.api.getTemplates().subscribe(t => this.templates = t);
    this.api.getSmtpConfigs().subscribe(s => this.smtpConfigs = s);
    this.api.getTags().subscribe(t => this.availableTags = t);

    if (this.campaignId) {
      this.api.getCampaign(this.campaignId).subscribe(c => {
        this.campaign = c;
        this.selectedSmtpId = c.smtpConfig?.id?.toString() || '';
      });
    }
  }

  goToStep(step: BuilderStep): void {
    this.completedSteps.add(this.currentStep);
    this.currentStep = step;
    this.contentTab = 'html';
    this.spamResult = null;
  }

  isStepDone(step: BuilderStep): boolean {
    return this.completedSteps.has(step) && step !== this.currentStep;
  }

  showPreview(): void { this.contentTab = 'preview'; }

  runSpamCheck(): void {
    this.contentTab = 'spam';
    if (!this.campaign.subject && !this.campaign.htmlContent) return;
    this.spamLoading = true;
    this.api.analyzeSpam(this.campaign.subject || '', this.campaign.htmlContent || '').subscribe({
      next: (r) => { this.spamResult = r; this.spamLoading = false; },
      error: () => this.spamLoading = false
    });
  }

  loadTemplate(event: Event): void {
    const id = parseInt((event.target as HTMLSelectElement).value);
    if (!id) return;
    this.api.getTemplate(id).subscribe(t => {
      this.campaign.htmlContent = t.htmlContent;
      this.campaign.textContent = t.textContent;
      if (!this.campaign.subject) this.campaign.subject = t.subject;
      this.toast.info('Template loaded');
    });
  }

  copyMergeTag(tag: string): void {
    navigator.clipboard.writeText(tag).then(() => this.toast.info('Copied', tag));
  }

  isTagSelected(tagId: number): boolean {
    return this.campaign.tagFilters?.includes(tagId) || false;
  }

  toggleTagFilter(tagId: number): void {
    const filters = this.campaign.tagFilters || [];
    const idx = filters.indexOf(tagId);
    if (idx >= 0) filters.splice(idx, 1);
    else filters.push(tagId);
    this.campaign.tagFilters = [...filters];
  }

  get selectedTagNames(): string {
    return this.availableTags
      .filter(t => this.campaign.tagFilters?.includes(t.id!))
      .map(t => t.name).join(', ') || 'None selected';
  }

  sendTest(): void {
    if (!this.campaignId) {
      this.saveDraftAndRun(() => this.sendTestEmail());
      return;
    }
    this.sendTestEmail();
  }

  private sendTestEmail(): void {
    this.testSending = true;
    this.api.sendTestEmail(this.campaignId!, this.testEmail).subscribe({
      next: () => { this.toast.success('Test email sent!', this.testEmail); this.testSending = false; },
      error: (e) => { this.toast.error('Failed to send test', e.error?.error); this.testSending = false; }
    });
  }

  saveDraft(): void {
    this.saving = true;
    this.buildAndSave('DRAFT', () => {
      this.toast.success('Draft saved');
      this.saving = false;
      this.router.navigate(['/campaigns']);
    });
  }

  saveAndSend(): void {
    this.saving = true;
    this.buildAndSave('DRAFT', (id) => {
      this.api.sendCampaign(id).subscribe({
        next: () => { this.toast.success('Campaign started!'); this.saving = false; this.router.navigate(['/campaigns']); },
        error: (e) => { this.toast.error('Failed to send', e.error?.message); this.saving = false; }
      });
    });
  }

  private saveDraftAndRun(fn: () => void): void {
    this.buildAndSave('DRAFT', () => fn());
  }

  private buildAndSave(status: string, callback: (id: number) => void): void {
    const data: any = { ...this.campaign, status };
    if (this.selectedSmtpId) data.smtpConfig = { id: +this.selectedSmtpId };

    const obs = this.campaignId
      ? this.api.updateCampaign(this.campaignId, data)
      : this.api.createCampaign(data);

    obs.subscribe({
      next: (c) => { this.campaignId = c.id!; callback(c.id!); },
      error: (e) => { this.toast.error('Save failed', e.message); this.saving = false; }
    });
  }
}
