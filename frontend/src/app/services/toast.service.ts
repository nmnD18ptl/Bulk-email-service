import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message?: string;
  duration?: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {

  toasts = signal<Toast[]>([]);

  show(type: Toast['type'], title: string, message?: string, duration = 4000): void {
    const id = Date.now().toString();
    const toast: Toast = { id, type, title, message, duration };
    this.toasts.update(t => [...t, toast]);

    if (duration > 0) {
      setTimeout(() => this.remove(id), duration);
    }
  }

  success(title: string, message?: string): void { this.show('success', title, message); }
  error(title: string, message?: string): void { this.show('error', title, message, 8000); }
  warning(title: string, message?: string): void { this.show('warning', title, message); }
  info(title: string, message?: string): void { this.show('info', title, message); }

  remove(id: string): void {
    this.toasts.update(t => t.filter(toast => toast.id !== id));
  }
}
