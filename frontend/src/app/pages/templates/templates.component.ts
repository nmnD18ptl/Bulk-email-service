import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { Template } from '../../models/models';

@Component({
  selector: 'app-templates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    .templates-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:20px; }
    .template-card {
      background:white; border-radius:10px; overflow:hidden; box-shadow:var(--shadow);
      transition:box-shadow 0.15s; cursor:pointer;
      &:hover { box-shadow:var(--shadow-md); }
      .template-preview {
        height:160px; background:var(--gray-100); overflow:hidden;
        display:flex; align-items:center; justify-content:center;
        position:relative;
        iframe { width:200%; height:200%; transform:scale(0.5); transform-origin:top left; border:none; pointer-events:none; }
        .preview-overlay { position:absolute; inset:0; background:transparent; }
      }
      .template-info { padding:14px;
        .template-name { font-weight:600; font-size:14px; color:var(--gray-800); }
        .template-cat { font-size:12px; color:var(--gray-400); margin-top:2px; }
        .template-actions { display:flex; gap:6px; margin-top:12px; }
      }
    }
    .built-in-badge {
      position:absolute; top:8px; right:8px;
      background:var(--primary); color:white; font-size:10px;
      padding:2px 8px; border-radius:100px; font-weight:600;
    }
    .html-editor { font-family:monospace; font-size:13px; min-height:400px; }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">Email Templates</div>
          <div class="page-subtitle">{{ templates.length }} templates available</div>
        </div>
        <button class="btn btn-primary" (click)="openNew()">
          <i class="fas fa-plus"></i> New Template
        </button>
      </div>

      <!-- Category filter -->
      <div style="display:flex;gap:8px;margin-bottom:20px;flex-wrap:wrap">
        @for (cat of categories; track cat) {
          <button class="btn btn-sm" [class.btn-primary]="selectedCategory === cat"
                  [class.btn-secondary]="selectedCategory !== cat"
                  (click)="filterByCategory(cat)">{{ cat }}</button>
        }
      </div>

      @if (loading) {
        <div style="text-align:center;padding:60px"><div class="spinner spinner-dark"></div></div>
      } @else {
        <div class="templates-grid">
          @for (template of filteredTemplates; track template.id) {
            <div class="template-card">
              <div class="template-preview">
                <iframe [srcdoc]="template.htmlContent"></iframe>
                <div class="preview-overlay"></div>
                @if (template.isBuiltIn) {
                  <span class="built-in-badge">Built-in</span>
                }
              </div>
              <div class="template-info">
                <div class="template-name">{{ template.name }}</div>
                <div class="template-cat">{{ template.category }} &bull; {{ template.subject }}</div>
                <div class="template-actions">
                  <button class="btn btn-sm btn-primary" (click)="useTemplate(template)">
                    <i class="fas fa-edit"></i> Use
                  </button>
                  <button class="btn btn-sm btn-secondary" (click)="previewTemplate(template)">
                    <i class="fas fa-eye"></i> Preview
                  </button>
                  @if (!template.isBuiltIn) {
                    <button class="btn btn-sm btn-secondary" (click)="editTemplate(template)">
                      <i class="fas fa-pencil-alt"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" (click)="deleteTemplate(template)">
                      <i class="fas fa-trash"></i>
                    </button>
                  }
                </div>
              </div>
            </div>
          }
        </div>
      }
    </div>

    <!-- Edit/New Modal -->
    @if (showEditor) {
      <div class="modal-overlay" (click)="showEditor = false">
        <div class="modal" style="max-width:800px" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ editingTemplate?.id ? 'Edit' : 'New' }} Template</h3>
            <button class="close-btn" (click)="showEditor = false"><i class="fas fa-times"></i></button>
          </div>
          <div class="modal-body">
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
              <div class="form-group">
                <label>Template Name *</label>
                <input class="form-control" [(ngModel)]="editingTemplate!.name">
              </div>
              <div class="form-group">
                <label>Category</label>
                <input class="form-control" [(ngModel)]="editingTemplate!.category" placeholder="Newsletter, Promotion...">
              </div>
              <div class="form-group" style="grid-column:1/-1">
                <label>Default Subject</label>
                <input class="form-control" [(ngModel)]="editingTemplate!.subject">
              </div>
              <div class="form-group" style="grid-column:1/-1">
                <label>HTML Content *</label>
                <textarea class="form-control html-editor" [(ngModel)]="editingTemplate!.htmlContent"></textarea>
              </div>
              <div class="form-group" style="grid-column:1/-1">
                <label>Plain Text Version</label>
                <textarea class="form-control" style="min-height:100px" [(ngModel)]="editingTemplate!.textContent"></textarea>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showEditor = false">Cancel</button>
            <button class="btn btn-primary" (click)="saveTemplate()" [disabled]="saving">
              <span *ngIf="saving" class="spinner"></span> Save Template
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Preview Modal -->
    @if (showPreview) {
      <div class="modal-overlay" (click)="showPreview = false">
        <div class="modal" style="max-width:700px" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ previewingTemplate?.name }}</h3>
            <button class="close-btn" (click)="showPreview = false"><i class="fas fa-times"></i></button>
          </div>
          <div class="modal-body" style="padding:0">
            <iframe [srcdoc]="previewingTemplate?.htmlContent" style="width:100%;height:500px;border:none"></iframe>
          </div>
        </div>
      </div>
    }
  `
})
export class TemplatesComponent implements OnInit {
  templates: Template[] = [];
  filteredTemplates: Template[] = [];
  loading = true;
  selectedCategory = 'All';
  categories = ['All', 'Welcome', 'Newsletter', 'Promotion', 'Reminder', 'Product Launch'];

  showEditor = false;
  showPreview = false;
  editingTemplate: Partial<Template> | null = null;
  previewingTemplate: Template | null = null;
  saving = false;

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit(): void { this.loadTemplates(); }

  loadTemplates(): void {
    this.loading = true;
    this.api.getTemplates().subscribe({
      next: (t) => { this.templates = t; this.filterByCategory('All'); this.loading = false; },
      error: () => this.loading = false
    });
  }

  filterByCategory(cat: string): void {
    this.selectedCategory = cat;
    this.filteredTemplates = cat === 'All'
      ? this.templates
      : this.templates.filter(t => t.category === cat);
  }

  openNew(): void {
    this.editingTemplate = { name: '', category: '', subject: '', htmlContent: '', textContent: '' };
    this.showEditor = true;
  }

  editTemplate(t: Template): void {
    this.editingTemplate = { ...t };
    this.showEditor = true;
  }

  useTemplate(t: Template): void {
    localStorage.setItem('selectedTemplate', JSON.stringify(t));
    this.toast.info('Template selected', 'Go to Campaigns to use it');
  }

  previewTemplate(t: Template): void {
    this.previewingTemplate = t;
    this.showPreview = true;
  }

  saveTemplate(): void {
    if (!this.editingTemplate?.name || !this.editingTemplate?.htmlContent) {
      this.toast.error('Name and HTML content are required');
      return;
    }
    this.saving = true;
    const obs = this.editingTemplate.id
      ? this.api.updateTemplate(this.editingTemplate.id, this.editingTemplate)
      : this.api.createTemplate(this.editingTemplate);
    obs.subscribe({
      next: () => { this.toast.success('Template saved'); this.showEditor = false; this.saving = false; this.loadTemplates(); },
      error: () => { this.saving = false; this.toast.error('Failed to save'); }
    });
  }

  deleteTemplate(t: Template): void {
    if (!confirm(`Delete "${t.name}"?`)) return;
    this.api.deleteTemplate(t.id!).subscribe(() => { this.toast.success('Deleted'); this.loadTemplates(); });
  }
}
