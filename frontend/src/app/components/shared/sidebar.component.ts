import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  styles: [`
    .sidebar {
      position: fixed; left: 0; top: 0; bottom: 0;
      width: var(--sidebar-width); background: #111827;
      display: flex; flex-direction: column; z-index: 100; overflow: hidden;
    }
    .sidebar-logo {
      padding: 20px 16px; border-bottom: 1px solid rgba(255,255,255,0.08);
      display: flex; align-items: center; gap: 10px;
    }
    .logo-icon { width:36px; height:36px; background:var(--primary); border-radius:10px;
      display:flex; align-items:center; justify-content:center; font-size:18px; color:white; }
    .logo-text {
      .app-name { font-size:15px; font-weight:700; color:white; }
      .app-version { font-size:11px; color:rgba(255,255,255,0.4); }
    }
    .nav-section { padding:8px; flex:1; overflow-y:auto; }
    .nav-label { font-size:10px; font-weight:600; color:rgba(255,255,255,0.3);
      text-transform:uppercase; letter-spacing:1px; padding:12px 8px 6px; }
    .nav-item { display:flex; align-items:center; gap:10px; padding:9px 12px;
      border-radius:8px; color:rgba(255,255,255,0.6); text-decoration:none;
      font-size:14px; font-weight:500; transition:all 0.15s; margin-bottom:2px;
      i { width:18px; text-align:center; font-size:15px; }
    }
    .nav-item:hover { background:rgba(255,255,255,0.08); color:rgba(255,255,255,0.9); }
    .nav-item.active { background:var(--primary); color:white; }
    .nav-badge { margin-left:auto; background:var(--danger); color:white;
      font-size:10px; padding:1px 6px; border-radius:10px; font-weight:600; }
    .sidebar-footer { padding:12px; border-top:1px solid rgba(255,255,255,0.08); }
    .user-info { display:flex; align-items:center; gap:8px; margin-bottom:8px;
      .avatar { width:32px; height:32px; background:var(--primary); border-radius:50%;
        display:flex; align-items:center; justify-content:center; color:white; font-size:13px; font-weight:700; }
      .user-details {
        .user-name { font-size:12px; font-weight:600; color:rgba(255,255,255,0.8); }
        .user-org { font-size:11px; color:rgba(255,255,255,0.4); }
      }
    }
    .plan-bar { background:rgba(255,255,255,0.08); border-radius:4px; height:4px; margin-bottom:8px;
      .plan-fill { background:var(--primary); border-radius:4px; height:4px; transition:width 0.3s; }
    }
    .logout-btn { display:flex; align-items:center; gap:8px; padding:7px 10px; border-radius:6px;
      color:rgba(255,255,255,0.4); font-size:12px; cursor:pointer; transition:all 0.15s; border:none;
      background:none; width:100%;
      &:hover { background:rgba(239,68,68,0.2); color:#f87171; }
    }
  `],
  template: `
    <nav class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon"><i class="fas fa-envelope"></i></div>
        <div class="logo-text">
          <div class="app-name">Bulk Email Pro</div>
          <div class="app-version">v2.0 Multi-Client</div>
        </div>
      </div>

      <div class="nav-section">
        <div class="nav-label">Main</div>
        <a class="nav-item" routerLink="/dashboard" routerLinkActive="active">
          <i class="fas fa-chart-pie"></i> Dashboard
        </a>

        <div class="nav-label">Email</div>
        <a class="nav-item" routerLink="/contacts" routerLinkActive="active">
          <i class="fas fa-users"></i> Contacts
        </a>
        <a class="nav-item" routerLink="/campaigns" routerLinkActive="active">
          <i class="fas fa-paper-plane"></i> Campaigns
        </a>
        <a class="nav-item" routerLink="/templates" routerLinkActive="active">
          <i class="fas fa-file-alt"></i> Templates
        </a>

        <div class="nav-label">Insights</div>
        <a class="nav-item" routerLink="/analytics" routerLinkActive="active">
          <i class="fas fa-chart-bar"></i> Analytics
        </a>

        <div class="nav-label">Delivery</div>
        <a class="nav-item" routerLink="/warmup" routerLinkActive="active">
          <i class="fas fa-fire"></i> IP Warmup
        </a>
        <a class="nav-item" routerLink="/settings" routerLinkActive="active">
          <i class="fas fa-cog"></i> Settings
        </a>

        @if (canManageUsers) {
          <a class="nav-item" routerLink="/users" routerLinkActive="active">
            <i class="fas fa-user-friends"></i> Team Members
          </a>
        }
      </div>

      <div class="sidebar-footer">
        @if (user) {
          <div class="user-info">
            <div class="avatar">{{ initials }}</div>
            <div class="user-details">
              <div class="user-name">{{ user.fullName }}</div>
              <div class="user-org">{{ org?.name }} · {{ org?.plan }}</div>
            </div>
          </div>
          @if (org) {
            <div style="font-size:10px;color:rgba(255,255,255,0.3);margin-bottom:4px">
              {{ org.emailsSentThisMonth }} / {{ org.monthlyEmailLimit === 2147483647 ? '∞' : org.monthlyEmailLimit }} emails this month
            </div>
            <div class="plan-bar">
              <div class="plan-fill" [style.width.%]="emailUsagePct"></div>
            </div>
          }
        }
        <button class="logout-btn" (click)="logout()">
          <i class="fas fa-sign-out-alt"></i> Sign Out
        </button>
      </div>
    </nav>
  `
})
export class SidebarComponent {
  constructor(private auth: AuthService) {}

  get user() { return this.auth.currentUser; }
  get org() { return this.auth.currentOrg; }

  get initials(): string {
    const u = this.user;
    if (!u) return '?';
    return ((u.firstName?.[0] ?? '') + (u.lastName?.[0] ?? '')).toUpperCase() || u.email[0].toUpperCase();
  }

  get canManageUsers(): boolean {
    return this.auth.canDo(['OWNER', 'ADMIN']);
  }

  get emailUsagePct(): number {
    const org = this.org;
    if (!org || org.monthlyEmailLimit === 2147483647) return 0;
    return Math.min((org.emailsSentThisMonth / org.monthlyEmailLimit) * 100, 100);
  }

  logout(): void { this.auth.logout(); }
}
