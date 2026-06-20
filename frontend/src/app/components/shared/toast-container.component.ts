import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    .toast-container {
      position: fixed;
      bottom: 20px; right: 20px;
      display: flex; flex-direction: column; gap: 10px;
      z-index: 9999;
      max-width: 440px;
    }
    .toast {
      display: flex; align-items: flex-start; gap: 12px;
      background: white;
      border-radius: 10px;
      padding: 14px 16px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.15);
      border-left: 4px solid;
      animation: slideIn 0.3s ease;
      cursor: pointer;
      min-width: 280px;

      &.success { border-color: var(--success); }
      &.error { border-color: var(--danger); }
      &.warning { border-color: var(--warning); }
      &.info { border-color: var(--primary); }

      .toast-icon {
        font-size: 16px; margin-top: 1px; flex-shrink: 0;
        &.success { color: var(--success); }
        &.error { color: var(--danger); }
        &.warning { color: var(--warning); }
        &.info { color: var(--primary); }
      }
      .toast-body {
        flex: 1;
        .toast-title { font-weight: 600; font-size: 14px; color: var(--gray-800); }
        .toast-message { font-size: 13px; color: var(--gray-500); margin-top: 4px; line-height: 1.5; word-break: break-word; }
      }
      .toast-close { color: var(--gray-400); background: none; border: none; cursor: pointer; padding: 0; font-size: 16px; }
    }
    @keyframes slideIn {
      from { transform: translateX(100%); opacity: 0; }
      to { transform: translateX(0); opacity: 1; }
    }
  `],
  template: `
    <div class="toast-container">
      @for (toast of toastService.toasts(); track toast.id) {
        <div class="toast {{ toast.type }}" (click)="toastService.remove(toast.id)">
          <i class="toast-icon {{ toast.type }} fas"
             [class.fa-check-circle]="toast.type === 'success'"
             [class.fa-exclamation-circle]="toast.type === 'error'"
             [class.fa-exclamation-triangle]="toast.type === 'warning'"
             [class.fa-info-circle]="toast.type === 'info'"></i>
          <div class="toast-body">
            <div class="toast-title">{{ toast.title }}</div>
            @if (toast.message) {
              <div class="toast-message">{{ toast.message }}</div>
            }
          </div>
          <button class="toast-close" (click)="toastService.remove(toast.id)">
            <i class="fas fa-times"></i>
          </button>
        </div>
      }
    </div>
  `
})
export class ToastContainerComponent {
  constructor(public toastService: ToastService) {}
}
