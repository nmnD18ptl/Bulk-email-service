import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  styles: [`
    .auth-page { min-height:100vh; display:flex; align-items:center; justify-content:center;
      background:linear-gradient(135deg,#1e3a5f 0%,#2d6a4f 100%); }
    .auth-card { background:white; border-radius:16px; padding:40px; width:100%; max-width:440px;
      box-shadow:0 20px 60px rgba(0,0,0,0.3); }
    .auth-logo { text-align:center; margin-bottom:28px;
      h1 { font-size:24px; font-weight:800; color:#1e3a5f; margin:0; }
      p { color:#64748b; font-size:14px; margin:4px 0 0; }
    }
    .form-row { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
    .plan-badge { background:#ecfdf5; color:#059669; border:1px solid #a7f3d0;
      border-radius:8px; padding:10px 14px; font-size:13px; margin-bottom:16px; }
    .auth-footer { text-align:center; margin-top:16px; font-size:14px; color:#64748b;
      a { color:#3B82F6; font-weight:600; text-decoration:none; }
    }
  `],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-logo">
          <h1>📧 Bulk Email Pro</h1>
          <p>Create your free account</p>
        </div>

        <div class="plan-badge">
          ✅ Free plan: 500 emails/month · 500 contacts · 1 SMTP config
        </div>

        @if (error) {
          <div class="alert alert-error" style="margin-bottom:16px">{{ error }}</div>
        }

        <div class="form-group">
          <label>Organization / Company Name *</label>
          <input class="form-control" [(ngModel)]="orgName" placeholder="Acme Exports Pvt Ltd">
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>First Name</label>
            <input class="form-control" [(ngModel)]="firstName" placeholder="Rahul">
          </div>
          <div class="form-group">
            <label>Last Name</label>
            <input class="form-control" [(ngModel)]="lastName" placeholder="Sharma">
          </div>
        </div>

        <div class="form-group">
          <label>Email *</label>
          <input class="form-control" type="email" [(ngModel)]="email" placeholder="you@company.com">
        </div>

        <div class="form-group">
          <label>Password *</label>
          <input class="form-control" type="password" [(ngModel)]="password"
            placeholder="Minimum 8 characters">
        </div>

        <button class="btn btn-primary" style="width:100%;margin-top:8px"
          (click)="register()" [disabled]="loading">
          @if (loading) { <span class="spinner"></span> } Create Account
        </button>

        <div class="auth-footer">
          Already have an account? <a routerLink="/login">Sign in</a>
        </div>
      </div>
    </div>
  `
})
export class RegisterComponent {
  orgName = '';
  firstName = '';
  lastName = '';
  email = '';
  password = '';
  loading = false;
  error = '';

  constructor(private auth: AuthService, private router: Router) {}

  register(): void {
    if (!this.orgName || !this.email || !this.password) {
      this.error = 'Organization name, email and password are required.';
      return;
    }
    if (this.password.length < 8) {
      this.error = 'Password must be at least 8 characters.';
      return;
    }
    this.loading = true;
    this.error = '';
    this.auth.register(this.orgName, this.email, this.password, this.firstName, this.lastName)
      .subscribe({
        next: () => { this.loading = false; this.router.navigate(['/']); },
        error: (e) => {
          this.loading = false;
          this.error = e.error?.error || 'Registration failed. Please try again.';
        }
      });
  }
}
