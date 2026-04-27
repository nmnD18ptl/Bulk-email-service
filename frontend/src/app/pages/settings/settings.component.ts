import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { SmtpConfig } from '../../models/models';

type SettingsTab = 'smtp' | 'general' | 'deliverability' | 'spf';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    .tab-layout {
      display: flex;
      gap: 20px;
      align-items: flex-start;
    }
    .settings-tabs {
      width: 200px;
      flex-shrink: 0;
      position: sticky;
      top: 0;
      background: var(--gray-50);
      padding: 4px 0;
      .tab-item {
        display: flex; align-items: center; gap: 10px;
        padding: 10px 14px; border-radius: 8px;
        cursor: pointer; font-size: 14px; font-weight: 500;
        color: var(--gray-500); margin-bottom: 4px; transition: all 0.15s;
        i { width: 16px; text-align: center; }
        &:hover { background: var(--gray-100); color: var(--gray-700); }
        &.active { background: #EFF6FF; color: var(--primary); }
      }
    }
    .settings-content { flex: 1; min-width: 0; }
    .smtp-card {
      background: white; border-radius: 10px; padding: 20px; box-shadow: var(--shadow);
      margin-bottom: 12px; border: 2px solid transparent;
      &.default-card { border-color: var(--success); }
      .smtp-header {
        display: flex; align-items: center; gap: 12px; margin-bottom: 12px;
        .smtp-icon { width: 40px; height: 40px; border-radius: 8px; background: #EFF6FF;
          color: var(--primary); display: flex; align-items: center; justify-content: center; }
        .smtp-meta { flex: 1; min-width: 0;
          .smtp-name { font-weight: 600; }
          .smtp-host { font-size: 12px; color: var(--gray-400); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        }
      }
    }
    .smtp-actions { display: flex; gap: 6px; flex-shrink: 0; flex-wrap: wrap; }
    .smtp-stats { display: flex; gap: 16px; font-size: 13px; color: var(--gray-500); flex-wrap: wrap; }
    .provider-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
      gap: 12px; margin-bottom: 16px;
    }
    .provider-btn {
      padding: 14px; border: 2px solid var(--gray-200); border-radius: 8px;
      background: white; cursor: pointer; text-align: center; transition: all 0.15s;
      &:hover { border-color: var(--primary); background: #EFF6FF; }
      &.selected { border-color: var(--primary); background: #EFF6FF; }
      .provider-name { font-size: 13px; font-weight: 600; margin-top: 6px; }
    }
    .dns-record {
      background: var(--gray-900); color: #10B981; font-family: monospace;
      border-radius: 8px; padding: 16px; font-size: 13px;
      white-space: pre-wrap; word-break: break-all; line-height: 1.6;
    }
    .guide-step {
      display: flex; gap: 16px; padding: 16px;
      border-bottom: 1px solid var(--gray-100); &:last-child { border: none; }
      .step-number { width: 28px; height: 28px; border-radius: 50%; background: var(--primary);
        color: white; font-size: 13px; font-weight: 700; display: flex; align-items: center;
        justify-content: center; flex-shrink: 0; margin-top: 2px; }
      .step-content { flex: 1; min-width: 0;
        strong { font-size: 14px; display: block; margin-bottom: 4px; }
        p { font-size: 13px; color: var(--gray-500); line-height: 1.6; }
      }
    }

    /* Responsive: stack tabs on top on narrow screens */
    @media (max-width: 700px) {
      .tab-layout { flex-direction: column; }
      .settings-tabs {
        width: 100%;
        position: static;
        display: flex;
        flex-direction: row;
        flex-wrap: wrap;
        gap: 4px;
        padding: 0 0 12px 0;
        background: transparent;
        .tab-item { flex: 1; justify-content: center; min-width: 100px; }
      }
    }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div class="page-title">Settings</div>
      </div>

      <div class="tab-layout">
        <!-- Tab nav -->
        <div class="settings-tabs">
          @for (tab of tabs; track tab.key) {
            <div class="tab-item" [class.active]="currentTab === tab.key" (click)="currentTab = tab.key">
              <i class="fas {{ tab.icon }}"></i> {{ tab.label }}
            </div>
          }
        </div>

        <div class="settings-content">

          <!-- SMTP Settings -->
          @if (currentTab === 'smtp') {
            <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
              <h3 style="font-size:16px;font-weight:600">SMTP Configurations</h3>
              <button class="btn btn-primary" (click)="openSmtpForm()">
                <i class="fas fa-plus"></i> Add SMTP
              </button>
            </div>

            @if (smtpConfigs.length === 0) {
              <div class="empty-state card">
                <i class="fas fa-server"></i>
                <h3>No SMTP configured</h3>
                <p>Add an SMTP server to start sending emails</p>
                <br>
                <button class="btn btn-primary" (click)="openSmtpForm()">Add SMTP Server</button>
              </div>
            }

            @for (smtp of smtpConfigs; track smtp.id) {
              <div class="smtp-card" [class.default-card]="smtp.isDefault">
                <div class="smtp-header">
                  <div class="smtp-icon"><i class="fas fa-server"></i></div>
                  <div class="smtp-meta">
                    <div class="smtp-name">{{ smtp.name }}
                      @if (smtp.isDefault) { <span class="badge badge-success" style="margin-left:8px">Default</span> }
                      @if (smtp.connectionTested) { <span class="badge badge-primary" style="margin-left:4px">Tested</span> }
                    </div>
                    <div class="smtp-host">{{ smtp.host }}:{{ smtp.port }} &bull; {{ smtp.securityType }}</div>
                  </div>
                  <div class="smtp-actions">
                    <button class="btn btn-sm btn-secondary" (click)="testConnection(smtp)">
                      <i class="fas fa-plug"></i> Test
                    </button>
                    <button class="btn btn-sm btn-secondary" (click)="editSmtp(smtp)">
                      <i class="fas fa-pencil-alt"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" (click)="deleteSmtp(smtp)">
                      <i class="fas fa-trash"></i>
                    </button>
                  </div>
                </div>
                <div class="smtp-stats">
                  <span><i class="fas fa-user"></i> {{ smtp.username }}</span>
                  <span><i class="fas fa-envelope"></i> {{ smtp.fromEmail || 'Not set' }}</span>
                  <span><i class="fas fa-tachometer-alt"></i> {{ smtp.dailyLimit }}/day, {{ smtp.hourlyLimit }}/hour</span>
                  <span style="margin-left:auto">Sent today: {{ smtp.sentToday || 0 }}</span>
                </div>
              </div>
            }
          }

          <!-- General Settings -->
          @if (currentTab === 'general') {
            <div class="card">
              <div class="card-header"><h3>General Settings</h3></div>
              <div style="display:grid;gap:16px">
                @for (setting of generalSettings; track setting.settingKey) {
                  <div class="form-group" style="margin-bottom:0">
                    <label>{{ getLabel(setting.settingKey) }}</label>
                    <input class="form-control" [(ngModel)]="setting.settingValue"
                           [placeholder]="getPlaceholder(setting.settingKey)">
                  </div>
                }
                <div style="display:flex;justify-content:flex-end">
                  <button class="btn btn-primary" (click)="saveGeneralSettings()">
                    <i class="fas fa-save"></i> Save Settings
                  </button>
                </div>
              </div>
            </div>
          }

          <!-- Deliverability -->
          @if (currentTab === 'deliverability') {
            <div class="card">
              <div class="card-header"><h3>Deliverability Checklist</h3></div>
              <div style="display:grid;gap:12px">
                @for (item of deliverabilityItems; track item.title) {
                  <div style="display:flex;align-items:flex-start;gap:14px;padding:14px;border-radius:8px;background:var(--gray-50)">
                    <div style="width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;flex-shrink:0"
                         [style.background]="item.done ? '#ECFDF5' : '#FEF2F2'">
                      <i class="fas" [class.fa-check]="item.done" [class.fa-times]="!item.done"
                         [style.color]="item.done ? 'var(--success)' : 'var(--danger)'"></i>
                    </div>
                    <div>
                      <strong style="font-size:14px">{{ item.title }}</strong>
                      <p style="font-size:13px;color:var(--gray-500);margin-top:3px">{{ item.description }}</p>
                    </div>
                    <span class="badge" style="margin-left:auto;flex-shrink:0"
                          [class.badge-success]="item.done" [class.badge-danger]="!item.done">
                      {{ item.done ? 'Done' : item.priority }}
                    </span>
                  </div>
                }
              </div>
            </div>
          }

          <!-- SPF/DKIM/DMARC Guide -->
          @if (currentTab === 'spf') {
            <!-- Domain Health Check -->
            <div class="card" style="margin-bottom:16px">
              <div class="card-header"><h3>Domain Health Check</h3></div>
              <div style="display:flex;gap:10px;align-items:flex-end;padding:16px">
                <div class="form-group" style="flex:1;margin:0">
                  <label>Your Sending Domain</label>
                  <input class="form-control" [(ngModel)]="domainToCheck" placeholder="yourdomain.com"
                    (keyup.enter)="checkDomainHealth()">
                </div>
                <button class="btn btn-primary" (click)="checkDomainHealth()" [disabled]="checkingDomainHealth">
                  @if (checkingDomainHealth) { <span class="spinner"></span> } Verify DNS
                </button>
              </div>
              @if (domainHealthResult) {
                <div style="padding:0 16px 16px">
                  @for (key of ['spf','dkim','dmarc']; track key) {
                    <div style="display:flex;align-items:center;gap:12px;padding:10px 0;border-top:1px solid var(--gray-200)">
                      <span style="width:20px;text-align:center">
                        @if (domainHealthResult[key]?.valid) {
                          <i class="fas fa-check-circle" style="color:var(--success)"></i>
                        } @else {
                          <i class="fas fa-times-circle" style="color:var(--danger)"></i>
                        }
                      </span>
                      <span style="font-weight:600;width:60px;text-transform:uppercase;font-size:13px">{{ key }}</span>
                      <span style="font-size:13px;color:var(--gray-600)">{{ domainHealthResult[key]?.message }}</span>
                    </div>
                  }
                </div>
              }
            </div>
            <div class="card">
              <div class="card-header">
                <h3>SPF / DKIM / DMARC Setup Guide</h3>
              </div>
              <div class="alert alert-info">
                <i class="fas fa-info-circle"></i>
                <div>Proper DNS authentication is <strong>critical</strong> for inbox delivery.
                Without these records, emails will likely land in spam.</div>
              </div>

              <!-- SPF -->
              <div style="margin-bottom:24px">
                <h4 style="font-size:15px;font-weight:600;margin-bottom:12px;display:flex;align-items:center;gap:8px">
                  <span style="background:#EFF6FF;color:var(--primary);padding:2px 10px;border-radius:4px;font-size:13px">SPF</span>
                  Sender Policy Framework
                </h4>
                <div class="guide-step">
                  <div class="step-number">1</div>
                  <div class="step-content">
                    <strong>Create a TXT record in your DNS</strong>
                    <p>Go to your domain's DNS settings and add a new TXT record:</p>
                    <div class="dns-record" style="margin-top:8px">Name: &#64;  (or your domain)
Type: TXT
Value: v=spf1 include:_spf.youremailprovider.com ~all</div>
                  </div>
                </div>
                <div class="guide-step">
                  <div class="step-number">2</div>
                  <div class="step-content">
                    <strong>Common provider SPF records</strong>
                    <p><strong>Gmail:</strong> <code>v=spf1 include:_spf.google.com ~all</code></p>
                    <p><strong>Amazon SES:</strong> <code>v=spf1 include:amazonses.com ~all</code></p>
                    <p><strong>Brevo:</strong> <code>v=spf1 include:spf.sendinblue.com ~all</code></p>
                    <p><strong>Custom SMTP:</strong> <code>v=spf1 a mx ip4:YOUR.SERVER.IP ~all</code></p>
                  </div>
                </div>
              </div>

              <!-- DKIM -->
              <div style="margin-bottom:24px">
                <h4 style="font-size:15px;font-weight:600;margin-bottom:12px;display:flex;align-items:center;gap:8px">
                  <span style="background:#F5F3FF;color:var(--secondary);padding:2px 10px;border-radius:4px;font-size:13px">DKIM</span>
                  DomainKeys Identified Mail
                </h4>
                <div class="guide-step">
                  <div class="step-number">1</div>
                  <div class="step-content">
                    <strong>Generate DKIM keys (if using custom SMTP)</strong>
                    <p>Most providers (Gmail, Amazon SES, Brevo) handle DKIM for you automatically in their dashboard.
                    For self-hosted SMTP, generate keys using:</p>
                    <div class="dns-record" style="margin-top:8px">openssl genrsa -out dkim_private.key 2048
openssl rsa -in dkim_private.key -pubout -out dkim_public.key</div>
                  </div>
                </div>
                <div class="guide-step">
                  <div class="step-number">2</div>
                  <div class="step-content">
                    <strong>Add DKIM TXT record to DNS</strong>
                    <div class="dns-record" style="margin-top:8px">Name: selector._domainkey.yourdomain.com
Type: TXT
Value: v=DKIM1; k=rsa; p=YOUR_PUBLIC_KEY_HERE</div>
                  </div>
                </div>
              </div>

              <!-- DMARC -->
              <div>
                <h4 style="font-size:15px;font-weight:600;margin-bottom:12px;display:flex;align-items:center;gap:8px">
                  <span style="background:#ECFDF5;color:var(--success);padding:2px 10px;border-radius:4px;font-size:13px">DMARC</span>
                  Domain-based Message Authentication
                </h4>
                <div class="guide-step">
                  <div class="step-number">1</div>
                  <div class="step-content">
                    <strong>Start with a monitoring policy</strong>
                    <div class="dns-record" style="margin-top:8px">Name: _dmarc.yourdomain.com
Type: TXT
Value: v=DMARC1; p=none; rua=mailto:dmarc&#64;yourdomain.com</div>
                  </div>
                </div>
                <div class="guide-step">
                  <div class="step-number">2</div>
                  <div class="step-content">
                    <strong>After monitoring (2-4 weeks), enforce policy</strong>
                    <div class="dns-record" style="margin-top:8px">Value: v=DMARC1; p=quarantine; pct=50; rua=mailto:dmarc&#64;yourdomain.com</div>
                    <p style="margin-top:8px">Then move to <code>p=reject</code> when confident.</p>
                  </div>
                </div>
              </div>
            </div>
          }
        </div>
      </div>
    </div>

    <!-- SMTP Form Modal -->
    @if (showSmtpForm) {
      <div class="modal-overlay" (click)="showSmtpForm = false">
        <div class="modal" style="max-width:640px" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ editingSmtp?.id ? 'Edit' : 'Add' }} SMTP Configuration</h3>
            <button class="close-btn" (click)="showSmtpForm = false"><i class="fas fa-times"></i></button>
          </div>
          <div class="modal-body">
            <!-- Provider presets -->
            <div class="form-group">
              <label>Provider Preset</label>
              <div class="provider-grid">
                @for (p of providerPresets; track p.type) {
                  <div class="provider-btn" [class.selected]="editingSmtp?.providerType === p.type"
                       (click)="applyPreset(p)">
                    <i class="fas {{ p.icon }}" style="font-size:20px;color:var(--primary)"></i>
                    <div class="provider-name">{{ p.name }}</div>
                  </div>
                }
              </div>
            </div>

            <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
              <div class="form-group">
                <label>Config Name *</label>
                <input class="form-control" [(ngModel)]="editingSmtp!.name" placeholder="My Gmail SMTP">
              </div>
              <div class="form-group">
                <label>Security</label>
                <select class="form-control" [(ngModel)]="editingSmtp!.securityType">
                  <option value="TLS">TLS (STARTTLS)</option>
                  <option value="SSL">SSL</option>
                  <option value="NONE">None</option>
                </select>
              </div>
              <div class="form-group">
                <label>SMTP Host *</label>
                <input class="form-control" [(ngModel)]="editingSmtp!.host" placeholder="smtp.gmail.com">
              </div>
              <div class="form-group">
                <label>Port *</label>
                <input type="number" class="form-control" [(ngModel)]="editingSmtp!.port">
              </div>
              <div class="form-group">
                <label>Username *</label>
                <input class="form-control" [(ngModel)]="editingSmtp!.username" placeholder="your@email.com">
              </div>
              <div class="form-group">
                <label>Password *</label>
                <input type="password" class="form-control" [(ngModel)]="smtpPassword" placeholder="App password">
              </div>
              <div class="form-group">
                <label>From Name</label>
                <input class="form-control" [(ngModel)]="editingSmtp!.fromName" placeholder="Your Company">
              </div>
              <div class="form-group">
                <label>From Email</label>
                <input class="form-control" [(ngModel)]="editingSmtp!.fromEmail" placeholder="you@domain.com">
              </div>
              <div class="form-group">
                <label>Daily Sending Limit</label>
                <input type="number" class="form-control" [(ngModel)]="editingSmtp!.dailyLimit">
              </div>
              <div class="form-group">
                <label>Hourly Sending Limit</label>
                <input type="number" class="form-control" [(ngModel)]="editingSmtp!.hourlyLimit">
              </div>
            </div>
            <div style="display:flex;align-items:center;gap:8px">
              <input type="checkbox" [(ngModel)]="editingSmtp!.isDefault">
              <label>Set as default SMTP</label>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="testNewSmtp()" [disabled]="testingConnection">
              <span *ngIf="testingConnection" class="spinner spinner-dark"></span>
              <i *ngIf="!testingConnection" class="fas fa-plug"></i>
              Test Connection
            </button>
            <button class="btn btn-secondary" (click)="showSmtpForm = false">Cancel</button>
            <button class="btn btn-primary" (click)="saveSmtp()" [disabled]="savingSmtp">
              <span *ngIf="savingSmtp" class="spinner"></span> Save
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class SettingsComponent implements OnInit {
  currentTab: SettingsTab = 'smtp';
  tabs = [
    { key: 'smtp' as SettingsTab, icon: 'fa-server', label: 'SMTP' },
    { key: 'general' as SettingsTab, icon: 'fa-cog', label: 'General' },
    { key: 'deliverability' as SettingsTab, icon: 'fa-shield-alt', label: 'Deliverability' },
    { key: 'spf' as SettingsTab, icon: 'fa-lock', label: 'SPF/DKIM/DMARC' }
  ];

  smtpConfigs: SmtpConfig[] = [];
  showSmtpForm = false;
  editingSmtp: Partial<SmtpConfig> | null = null;
  smtpPassword = '';
  savingSmtp = false;
  testingConnection = false;

  generalSettings: any[] = [];

  providerPresets = [
    { type: 'GMAIL', name: 'Gmail', icon: 'fa-envelope', host: 'smtp.gmail.com', port: 587, securityType: 'TLS' },
    { type: 'AMAZON_SES', name: 'Amazon SES', icon: 'fa-amazon', host: 'email-smtp.us-east-1.amazonaws.com', port: 587, securityType: 'TLS' },
    { type: 'BREVO', name: 'Brevo', icon: 'fa-paper-plane', host: 'smtp-relay.brevo.com', port: 587, securityType: 'TLS' },
    { type: 'MAILGUN', name: 'Mailgun', icon: 'fa-bullhorn', host: 'smtp.mailgun.org', port: 587, securityType: 'TLS' },
    { type: 'SENDGRID', name: 'SendGrid', icon: 'fa-send', host: 'smtp.sendgrid.net', port: 587, securityType: 'TLS' },
    { type: 'CUSTOM', name: 'Custom', icon: 'fa-server', host: '', port: 587, securityType: 'TLS' }
  ];

  deliverabilityItems = [
    { title: 'SPF Record', description: 'Add a TXT record to your DNS to authorize email servers', done: false, priority: 'CRITICAL' },
    { title: 'DKIM Signing', description: 'Sign outgoing emails with a private key for authenticity', done: false, priority: 'CRITICAL' },
    { title: 'DMARC Policy', description: 'Set a policy for unauthenticated email from your domain', done: false, priority: 'HIGH' },
    { title: 'Custom Sending Domain', description: 'Send from your own domain, not a shared one', done: false, priority: 'HIGH' },
    { title: 'Unsubscribe Link', description: 'Every email must include a working unsubscribe link', done: true, priority: 'CRITICAL' },
    { title: 'Physical Address', description: 'Include your physical mailing address (CAN-SPAM)', done: true, priority: 'REQUIRED' },
    { title: 'IP Warmup', description: 'Gradually increase sending volume from new IPs', done: false, priority: 'IMPORTANT' },
    { title: 'Bounce Handling', description: 'Remove hard bounces immediately from your list', done: true, priority: 'HIGH' },
    { title: 'List Hygiene', description: 'Regularly remove inactive and invalid emails', done: false, priority: 'MEDIUM' }
  ];

  domainToCheck = '';
  domainHealthResult: any = null;
  checkingDomainHealth = false;

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit(): void {
    this.loadSmtpConfigs();
    this.api.getSettings('general').subscribe(s => this.generalSettings = s);
  }

  loadSmtpConfigs(): void {
    this.api.getSmtpConfigs().subscribe(c => this.smtpConfigs = c);
  }

  openSmtpForm(): void {
    this.editingSmtp = { name: '', host: '', port: 587, securityType: 'TLS', providerType: 'CUSTOM', dailyLimit: 500, hourlyLimit: 100 };
    this.smtpPassword = '';
    this.showSmtpForm = true;
  }

  editSmtp(smtp: SmtpConfig): void {
    this.editingSmtp = { ...smtp };
    this.smtpPassword = '';
    this.showSmtpForm = true;
  }

  applyPreset(preset: any): void {
    this.editingSmtp = {
      ...this.editingSmtp,
      host: preset.host,
      port: preset.port,
      securityType: preset.securityType,
      providerType: preset.type
    };
  }

  testNewSmtp(): void {
    if (!this.editingSmtp?.host || !this.editingSmtp?.username || !this.smtpPassword) {
      this.toast.warning('Fill in host, username, and password first');
      return;
    }
    this.testingConnection = true;
    this.api.testSmtpParams({
      host: this.editingSmtp.host,
      port: this.editingSmtp.port,
      username: this.editingSmtp.username,
      password: this.smtpPassword,
      securityType: this.editingSmtp.securityType
    }).subscribe({
      next: (r) => {
        this.testingConnection = false;
        if (r.success) this.toast.success('Connection successful!');
        else this.toast.error('Connection failed', r.message);
      },
      error: () => { this.testingConnection = false; this.toast.error('Connection failed'); }
    });
  }

  testConnection(smtp: SmtpConfig): void {
    this.api.testSmtpConnection(smtp.id!).subscribe(r => {
      if (r.success) this.toast.success('Connection OK', smtp.name);
      else this.toast.error('Connection failed', smtp.name);
      this.loadSmtpConfigs();
    });
  }

  saveSmtp(): void {
    if (!this.editingSmtp?.name || !this.editingSmtp?.host || !this.editingSmtp?.username) {
      this.toast.error('Name, host and username are required');
      return;
    }
    this.savingSmtp = true;
    const payload = { ...this.editingSmtp, password: this.smtpPassword };
    const obs = this.editingSmtp.id
      ? this.api.updateSmtpConfig(this.editingSmtp.id, payload)
      : this.api.createSmtpConfig(payload);
    obs.subscribe({
      next: () => {
        this.toast.success('SMTP saved');
        this.showSmtpForm = false;
        this.savingSmtp = false;
        this.loadSmtpConfigs();
      },
      error: () => { this.savingSmtp = false; this.toast.error('Failed to save'); }
    });
  }

  deleteSmtp(smtp: SmtpConfig): void {
    if (!confirm(`Delete "${smtp.name}"?`)) return;
    this.api.deleteSmtpConfig(smtp.id!).subscribe(() => { this.toast.success('Deleted'); this.loadSmtpConfigs(); });
  }

  saveGeneralSettings(): void {
    const data: Record<string, string> = {};
    this.generalSettings.forEach(s => { if (s.settingValue !== undefined) data[s.settingKey] = s.settingValue; });
    this.api.bulkUpdateSettings(data).subscribe(() => this.toast.success('Settings saved'));
  }

  getLabel(key: string): string {
    const labels: Record<string, string> = {
      'company.name': 'Company Name',
      'company.address': 'Physical Address (CAN-SPAM)',
      'default.from.name': 'Default From Name',
      'default.from.email': 'Default From Email',
      'tracking.base.url': 'Tracking Base URL',
      'batch.default.size': 'Default Batch Size',
      'batch.default.delay': 'Default Batch Delay (seconds)',
      'inter.email.delay': 'Inter-Email Delay (ms)'
    };
    return labels[key] || key;
  }

  checkDomainHealth(): void {
    if (!this.domainToCheck.trim()) {
      this.toast.warning('Enter a domain name first');
      return;
    }
    this.checkingDomainHealth = true;
    this.domainHealthResult = null;
    this.api.checkDomainHealth(this.domainToCheck.trim()).subscribe({
      next: (r) => { this.domainHealthResult = r; this.checkingDomainHealth = false; },
      error: () => { this.checkingDomainHealth = false; this.toast.error('Domain health check failed'); }
    });
  }

  getPlaceholder(key: string): string {
    const ph: Record<string, string> = {
      'company.name': 'Your Company Name',
      'company.address': '123 Main St, City, State, Country',
      'default.from.email': 'noreply@yourdomain.com',
      'tracking.base.url': 'http://localhost:8080'
    };
    return ph[key] || '';
  }
}
