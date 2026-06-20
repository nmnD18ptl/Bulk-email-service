import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  styles: [`
    .auth-page { min-height:100vh; display:flex; align-items:center; justify-content:center;
      background:linear-gradient(135deg,#1e3a5f 0%,#2d6a4f 100%); }
    .auth-card { background:white; border-radius:16px; padding:40px; width:100%; max-width:420px;
      box-shadow:0 20px 60px rgba(0,0,0,0.3); }
    .auth-logo { text-align:center; margin-bottom:32px;
      h1 { font-size:24px; font-weight:800; color:#1e3a5f; margin:0; }
      p { color:#64748b; font-size:14px; margin:4px 0 0; }
    }
    .auth-footer { text-align:center; margin-top:20px; font-size:14px; color:#64748b;
      a { color:#3B82F6; font-weight:600; text-decoration:none; }
    }
  `],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-logo">
          <h1>📧 Bulk Email Pro</h1>
          <p>Sign in to your account</p>
        </div>

        @if (error) {
          <div class="alert alert-error" style="margin-bottom:16px">{{ error }}</div>
        }

        <div class="form-group">
          <label>Email</label>
          <input class="form-control" type="email" [(ngModel)]="email"
            placeholder="you@company.com" (keyup.enter)="login()">
        </div>
        <div class="form-group">
          <label>Password</label>
          <input class="form-control" type="password" [(ngModel)]="password"
            placeholder="Your password" (keyup.enter)="login()">
        </div>

        <button class="btn btn-primary" style="width:100%;margin-top:8px"
          (click)="login()" [disabled]="loading">
          @if (loading) { <span class="spinner"></span> } Sign In
        </button>

        <div class="auth-footer">
          Don't have an account? <a routerLink="/register">Create one free</a>
        </div>
      </div>
    </div>
  `
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;
  error = '';

  constructor(
    private auth: AuthService,
    private router: Router,
    private toast: ToastService
  ) {}

  login(): void {
    if (!this.email || !this.password) {
      this.error = 'Please enter your email and password.';
      return;
    }
    this.loading = true;
    this.error = '';
    this.auth.login(this.email, this.password).subscribe({
      next: () => { this.loading = false; this.router.navigate(['/']); },
      error: (e) => {
        this.loading = false;
        this.error = e.error?.error || 'Login failed. Please check your credentials.';
      }
    });
  }
}
