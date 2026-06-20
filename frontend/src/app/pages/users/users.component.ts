import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { environment } from '../../../environments/environment';

interface TeamMember {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: 'OWNER' | 'ADMIN' | 'OPERATOR' | 'VIEWER';
  active: boolean;
  lastLoginAt: string | null;
}

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    .page-header { margin-bottom:24px; display:flex; justify-content:space-between; align-items:flex-start; }
    .page-title { font-size:22px; font-weight:700; color:#1e293b; margin:0; }
    .page-subtitle { font-size:14px; color:#64748b; margin:4px 0 0; }
    .card { background:white; border-radius:12px; border:1px solid #e2e8f0; overflow:hidden; }
    .table-wrap { overflow-x:auto; }
    table { width:100%; border-collapse:collapse; }
    th { background:#f8fafc; padding:11px 16px; text-align:left; font-size:12px; font-weight:600;
      color:#64748b; text-transform:uppercase; letter-spacing:.5px; border-bottom:1px solid #e2e8f0; }
    td { padding:14px 16px; border-bottom:1px solid #f1f5f9; font-size:14px; color:#334155; vertical-align:middle; }
    tr:last-child td { border-bottom:none; }
    tr:hover td { background:#f8fafc; }
    .avatar { width:34px; height:34px; border-radius:50%; background:var(--primary); color:white;
      display:flex; align-items:center; justify-content:center; font-size:13px; font-weight:700; flex-shrink:0; }
    .member-cell { display:flex; align-items:center; gap:10px; }
    .member-name { font-weight:600; color:#1e293b; }
    .member-email { font-size:12px; color:#64748b; }
    .role-badge { display:inline-flex; padding:3px 10px; border-radius:20px; font-size:11px; font-weight:600; }
    .role-OWNER  { background:#fef3c7; color:#92400e; }
    .role-ADMIN  { background:#dbeafe; color:#1d4ed8; }
    .role-OPERATOR { background:#dcfce7; color:#166534; }
    .role-VIEWER { background:#f1f5f9; color:#475569; }
    .btn-sm { padding:5px 12px; font-size:12px; }
    .modal-backdrop { position:fixed; inset:0; background:rgba(0,0,0,0.5); z-index:1000;
      display:flex; align-items:center; justify-content:center; }
    .modal { background:white; border-radius:16px; padding:32px; width:100%; max-width:460px;
      box-shadow:0 20px 60px rgba(0,0,0,0.2); }
    .modal-title { font-size:18px; font-weight:700; color:#1e293b; margin:0 0 20px; }
    .modal-footer { display:flex; gap:10px; justify-content:flex-end; margin-top:24px; }
    .empty-state { text-align:center; padding:48px 24px; color:#64748b; }
    .empty-icon { font-size:40px; color:#cbd5e1; margin-bottom:12px; }
  `],
  template: `
    <div>
      <div class="page-header">
        <div>
          <h1 class="page-title">Team Members</h1>
          <p class="page-subtitle">Manage who has access to your organization</p>
        </div>
        <button class="btn btn-primary" (click)="openInvite()" *ngIf="isOwnerOrAdmin">
          <i class="fas fa-user-plus"></i> Invite Member
        </button>
      </div>

      <div class="card">
        <div class="table-wrap">
          <table *ngIf="members.length > 0; else emptyTpl">
            <thead>
              <tr>
                <th>Member</th>
                <th>Role</th>
                <th>Last Login</th>
                <th *ngIf="isOwnerOrAdmin"></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let m of members">
                <td>
                  <div class="member-cell">
                    <div class="avatar">{{ initials(m) }}</div>
                    <div>
                      <div class="member-name">{{ m.fullName || m.email }}</div>
                      <div class="member-email">{{ m.email }}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="role-badge role-{{ m.role }}">{{ m.role }}</span>
                </td>
                <td>{{ m.lastLoginAt ? (m.lastLoginAt | date:'dd MMM yyyy, h:mm a') : 'Never' }}</td>
                <td *ngIf="isOwnerOrAdmin">
                  <button class="btn btn-outline btn-sm" *ngIf="m.role !== 'OWNER'"
                    (click)="confirmRemove(m)" style="color:#ef4444;border-color:#fca5a5">
                    <i class="fas fa-trash"></i>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <ng-template #emptyTpl>
            <div class="empty-state" *ngIf="!loading">
              <div class="empty-icon"><i class="fas fa-users"></i></div>
              <p>No team members yet. Invite your first member!</p>
            </div>
            <div class="empty-state" *ngIf="loading">
              <span class="spinner"></span> Loading...
            </div>
          </ng-template>
        </div>
      </div>
    </div>

    <!-- Invite Modal -->
    <div class="modal-backdrop" *ngIf="showInvite" (click)="showInvite=false">
      <div class="modal" (click)="$event.stopPropagation()">
        <h3 class="modal-title">Invite Team Member</h3>

        <div *ngIf="inviteResult" class="alert alert-success" style="margin-bottom:16px">
          {{ inviteResult }}
        </div>
        <div *ngIf="inviteError" class="alert alert-error" style="margin-bottom:16px">
          {{ inviteError }}
        </div>

        <div class="form-row" style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
          <div class="form-group">
            <label>First Name</label>
            <input class="form-control" [(ngModel)]="invite.firstName" placeholder="Raj">
          </div>
          <div class="form-group">
            <label>Last Name</label>
            <input class="form-control" [(ngModel)]="invite.lastName" placeholder="Kumar">
          </div>
        </div>
        <div class="form-group">
          <label>Email *</label>
          <input class="form-control" type="email" [(ngModel)]="invite.email" placeholder="colleague@company.com">
        </div>
        <div class="form-group">
          <label>Role</label>
          <select class="form-control" [(ngModel)]="invite.role">
            <option value="ADMIN">Admin — full access except billing</option>
            <option value="OPERATOR">Operator — create & send campaigns</option>
            <option value="VIEWER">Viewer — read only</option>
          </select>
        </div>
        <div style="font-size:12px;color:#64748b;margin-top:-8px;margin-bottom:4px">
          A temporary password <strong>ChangeMe&#64;123</strong> will be set. Share it with the invitee.
        </div>

        <div class="modal-footer">
          <button class="btn btn-outline" (click)="showInvite=false">Cancel</button>
          <button class="btn btn-primary" (click)="sendInvite()" [disabled]="inviting">
            <span *ngIf="inviting" class="spinner"></span>
            Send Invite
          </button>
        </div>
      </div>
    </div>

    <!-- Remove Confirm Modal -->
    <div class="modal-backdrop" *ngIf="memberToRemove" (click)="memberToRemove=null">
      <div class="modal" (click)="$event.stopPropagation()">
        <h3 class="modal-title">Remove Team Member?</h3>
        <p style="color:#64748b;margin:0 0 8px">
          Are you sure you want to remove <strong>{{ memberToRemove?.fullName || memberToRemove?.email }}</strong>
          from your organization? They will immediately lose access.
        </p>
        <div class="modal-footer">
          <button class="btn btn-outline" (click)="memberToRemove=null">Cancel</button>
          <button class="btn btn-primary" (click)="removeMember()" [disabled]="removing"
            style="background:#ef4444;border-color:#ef4444">
            <span *ngIf="removing" class="spinner"></span>
            Remove
          </button>
        </div>
      </div>
    </div>
  `
})
export class UsersComponent implements OnInit {
  members: TeamMember[] = [];
  loading = false;

  showInvite = false;
  invite = { firstName: '', lastName: '', email: '', role: 'OPERATOR' };
  inviting = false;
  inviteResult = '';
  inviteError = '';

  memberToRemove: TeamMember | null = null;
  removing = false;

  private apiBase = environment.apiUrl.replace('/api', '');

  constructor(private http: HttpClient, private auth: AuthService, private toast: ToastService) {}

  ngOnInit(): void { this.load(); }

  get isOwnerOrAdmin(): boolean { return this.auth.canDo(['OWNER', 'ADMIN']); }

  initials(m: TeamMember): string {
    return ((m.firstName?.[0] ?? '') + (m.lastName?.[0] ?? '')).toUpperCase() || m.email[0].toUpperCase();
  }

  load(): void {
    this.loading = true;
    this.http.get<TeamMember[]>(`${this.apiBase}/api/auth/users`).subscribe({
      next: ms => { this.members = ms; this.loading = false; },
      error: () => { this.loading = false; this.toast.error('Failed to load team members'); }
    });
  }

  openInvite(): void {
    this.invite = { firstName: '', lastName: '', email: '', role: 'OPERATOR' };
    this.inviteResult = '';
    this.inviteError = '';
    this.showInvite = true;
  }

  sendInvite(): void {
    if (!this.invite.email) { this.inviteError = 'Email is required.'; return; }
    this.inviting = true;
    this.inviteResult = '';
    this.inviteError = '';
    this.http.post<any>(`${this.apiBase}/api/auth/invite`, this.invite).subscribe({
      next: (r) => {
        this.inviting = false;
        this.inviteResult = r.message || 'User invited successfully.';
        this.load();
      },
      error: (e) => {
        this.inviting = false;
        this.inviteError = e.error?.error || 'Failed to invite user.';
      }
    });
  }

  confirmRemove(m: TeamMember): void { this.memberToRemove = m; }

  removeMember(): void {
    if (!this.memberToRemove) return;
    this.removing = true;
    this.http.delete<void>(`${this.apiBase}/api/auth/users/${this.memberToRemove.id}`).subscribe({
      next: () => {
        this.removing = false;
        this.memberToRemove = null;
        this.toast.success('Member removed');
        this.load();
      },
      error: () => {
        this.removing = false;
        this.toast.error('Failed to remove member');
      }
    });
  }
}
