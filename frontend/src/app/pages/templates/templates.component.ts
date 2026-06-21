import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { Template, SpamAnalysis } from '../../models/models';

@Component({
  selector: 'app-templates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    /* ── Grid ── */
    .templates-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 24px;
    }

    /* ── Card ── */
    .template-card {
      background: white;
      border-radius: 12px;
      overflow: hidden;
      border: 1px solid var(--gray-200);
      box-shadow: 0 1px 4px rgba(0,0,0,0.06);
      transition: box-shadow 0.2s, transform 0.2s;
      display: flex;
      flex-direction: column;
    }
    .template-card:hover { box-shadow: 0 8px 28px rgba(0,0,0,0.1); transform: translateY(-2px); }

    .template-preview {
      height: 200px;
      background: var(--gray-50);
      overflow: hidden;
      position: relative;
      cursor: pointer;
      flex-shrink: 0;
    }
    .template-preview iframe {
      width: 200%; height: 200%;
      transform: scale(0.5); transform-origin: top left;
      border: none; pointer-events: none;
    }
    .preview-hover-overlay {
      position: absolute; inset: 0;
      background: rgba(15,23,42,0.5);
      display: flex; align-items: center; justify-content: center; gap: 10px;
      opacity: 0; transition: opacity 0.2s;
    }
    .template-card:hover .preview-hover-overlay { opacity: 1; }

    .card-badge {
      position: absolute; top: 10px; right: 10px;
      background: var(--primary); color: white; font-size: 10px;
      padding: 2px 8px; border-radius: 100px; font-weight: 700; letter-spacing: 0.4px;
    }
    .cat-badge {
      position: absolute; bottom: 10px; left: 10px;
      color: white; font-size: 10px; font-weight: 600;
      padding: 3px 10px; border-radius: 100px;
    }

    .template-info { padding: 14px 16px; flex: 1; display: flex; flex-direction: column; gap: 10px; }
    .template-name { font-weight: 700; font-size: 15px; color: var(--gray-800); }
    .template-sub  { font-size: 12px; color: var(--gray-400); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

    .card-actions { display: flex; gap: 6px; align-items: center; margin-top: auto; }
    .action-icon-btn {
      width: 32px; height: 32px; border-radius: 6px;
      border: 1px solid var(--gray-200); background: white;
      color: var(--gray-500); cursor: pointer; transition: all 0.15s;
      display: flex; align-items: center; justify-content: center; font-size: 13px;
    }
    .action-icon-btn:hover { border-color: var(--primary); color: var(--primary); background: #EFF6FF; }
    .action-icon-btn.danger:hover { border-color: var(--danger); color: var(--danger); background: #FEF2F2; }

    /* ── Toolbar ── */
    .templates-toolbar {
      display: flex; gap: 12px; margin-bottom: 16px; align-items: center; flex-wrap: wrap;
    }
    .cat-chips { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 20px; }
    .cat-chip {
      padding: 5px 14px; border-radius: 100px; font-size: 13px;
      font-weight: 500; cursor: pointer;
      border: 1.5px solid var(--gray-200); background: white; color: var(--gray-600);
      transition: all 0.15s;
    }
    .cat-chip.active  { background: var(--primary); color: white; border-color: var(--primary); }
    .cat-chip:hover:not(.active) { border-color: var(--primary); color: var(--primary); }

    /* ── Editor Modal ── */
    .editor-modal {
      max-width: 1220px; width: 96vw; height: 92vh;
      display: flex; flex-direction: column; overflow: hidden;
    }
    .editor-body { flex: 1; display: flex; overflow: hidden; }
    .editor-left {
      flex: 0 0 46%; padding: 20px;
      overflow-y: auto; border-right: 1px solid var(--gray-200);
      display: flex; flex-direction: column; gap: 14px;
    }
    .editor-right { flex: 1; display: flex; flex-direction: column; background: var(--gray-50); min-width: 0; }

    /* Merge tags */
    .merge-tags-bar { display: flex; gap: 5px; flex-wrap: wrap; }
    .mtag {
      padding: 3px 10px; font-size: 11px; border-radius: 100px;
      background: #EFF6FF; color: var(--primary);
      border: 1px solid #BFDBFE; cursor: pointer;
      font-family: monospace; white-space: nowrap;
      transition: all 0.15s; font-weight: 600;
    }
    .mtag:hover { background: var(--primary); color: white; border-color: var(--primary); }

    /* HTML textarea */
    .html-editor {
      font-family: 'Consolas', 'Monaco', monospace; font-size: 12px;
      line-height: 1.6; resize: vertical;
    }

    /* Device toggle */
    .device-toggle {
      display: flex; background: white;
      border-bottom: 1px solid var(--gray-200);
    }
    .device-btn {
      flex: 1; padding: 10px 8px; border: none; background: none;
      font-size: 12px; cursor: pointer; color: var(--gray-500);
      border-bottom: 2px solid transparent; transition: all 0.15s;
      font-weight: 500;
    }
    .device-btn.active { color: var(--primary); border-bottom-color: var(--primary); background: #F8FAFF; }
    .device-btn:hover:not(.active) { color: var(--gray-700); background: var(--gray-50); }

    /* Preview frame */
    .preview-frame-outer {
      flex: 1; overflow: auto; display: flex;
      justify-content: center; padding: 20px;
      background: #E5E7EB;
    }
    .preview-frame-inner {
      background: white; transition: width 0.3s;
      box-shadow: 0 2px 20px rgba(0,0,0,0.18);
      border-radius: 2px;
    }

    /* Spam */
    .spam-chip {
      display: flex; align-items: center; gap: 6px;
      padding: 4px 12px; border-radius: 100px; font-size: 12px; font-weight: 600;
    }

    /* Preview modal */
    .preview-modal {
      max-width: 820px; width: 94vw; height: 92vh;
      display: flex; flex-direction: column; overflow: hidden;
    }
    .preview-body {
      flex: 1; overflow: auto; background: #E5E7EB;
      display: flex; justify-content: center; padding: 20px;
    }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">Email Templates</div>
          <div class="page-subtitle">{{ filteredTemplates.length }} of {{ templates.length }} templates</div>
        </div>
        <button class="btn btn-primary" (click)="openNew()">
          <i class="fas fa-plus"></i> New Template
        </button>
      </div>

      <!-- Toolbar: search -->
      <div class="templates-toolbar">
        <div class="search-bar" style="width:280px">
          <i class="fas fa-search"></i>
          <input placeholder="Search by name or subject..." [(ngModel)]="searchQuery" (input)="applyFilters()">
        </div>
        <div style="margin-left:auto;font-size:13px;color:var(--gray-400)">
          Click any template to preview
        </div>
      </div>

      <!-- Category chips -->
      <div class="cat-chips">
        @for (cat of categories; track cat) {
          <button class="cat-chip" [class.active]="selectedCategory === cat"
                  (click)="filterByCategory(cat)">
            {{ cat }}
          </button>
        }
      </div>

      <!-- Loading -->
      @if (loading) {
        <div style="text-align:center;padding:80px">
          <div class="spinner spinner-dark"></div>
        </div>

      } @else if (filteredTemplates.length === 0) {
        <!-- Empty state -->
        <div class="empty-state">
          <i class="fas fa-file-alt"></i>
          <h3>No templates found</h3>
          <p>{{ searchQuery ? 'Try a different search term' : 'Create your first template to get started' }}</p>
          @if (!searchQuery) {
            <button class="btn btn-primary" (click)="openNew()">
              <i class="fas fa-plus"></i> Create Template
            </button>
          }
        </div>

      } @else {
        <!-- Template Grid -->
        <div class="templates-grid">
          @for (tmpl of filteredTemplates; track tmpl.id) {
            <div class="template-card">

              <!-- Preview thumbnail -->
              <div class="template-preview" (click)="previewTemplate(tmpl)">
                <iframe [srcdoc]="tmpl.htmlContent" loading="lazy"></iframe>

                @if (tmpl.isBuiltIn) {
                  <span class="card-badge">BUILT-IN</span>
                }
                @if (tmpl.category) {
                  <span class="cat-badge" [style.background]="getCategoryColor(tmpl.category)">
                    {{ tmpl.category }}
                  </span>
                }

                <div class="preview-hover-overlay">
                  <button class="btn btn-sm" style="background:white;color:var(--gray-800)" (click)="previewTemplate(tmpl, $event)">
                    <i class="fas fa-eye"></i> Preview
                  </button>
                </div>
              </div>

              <!-- Info -->
              <div class="template-info">
                <div>
                  <div class="template-name">{{ tmpl.name }}</div>
                  @if (tmpl.subject) {
                    <div class="template-sub" [title]="tmpl.subject">{{ tmpl.subject }}</div>
                  }
                </div>

                <!-- Actions -->
                <div class="card-actions">
                  <button class="btn btn-sm btn-primary" style="flex:1" (click)="useTemplate(tmpl, $event)">
                    <i class="fas fa-paper-plane"></i> Use
                  </button>
                  <button class="action-icon-btn" (click)="previewTemplate(tmpl, $event)" title="Preview">
                    <i class="fas fa-eye"></i>
                  </button>
                  <button class="action-icon-btn" (click)="cloneTemplate(tmpl, $event)" title="Duplicate">
                    <i class="fas fa-copy"></i>
                  </button>
                  @if (!tmpl.isBuiltIn) {
                    <button class="action-icon-btn" (click)="editTemplate(tmpl, $event)" title="Edit HTML">
                      <i class="fas fa-pencil-alt"></i>
                    </button>
                    <button class="action-icon-btn danger" (click)="deleteTemplate(tmpl, $event)" title="Delete">
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


    <!-- ════════════════════════════════════════════════════
         EDITOR MODAL — Split view: HTML editor | Live preview
         ════════════════════════════════════════════════════ -->
    @if (showEditor) {
      <div class="modal-overlay" (click)="showEditor = false">
        <div class="modal editor-modal" (click)="$event.stopPropagation()">

          <!-- Header -->
          <div class="modal-header">
            <h3 style="margin:0">
              {{ editingTemplate?.id ? 'Edit Template' : 'New Template' }}
            </h3>
            <div style="display:flex;align-items:center;gap:8px">
              <!-- Spam check -->
              <button class="btn btn-sm btn-secondary" (click)="checkSpamScore()"
                      [disabled]="checkingSpam || !editingTemplate?.htmlContent">
                @if (checkingSpam) {
                  <span class="spinner"></span>
                } @else {
                  <i class="fas fa-shield-alt"></i>
                }
                Spam Check
              </button>
              <!-- Spam result chip -->
              @if (spamResult) {
                <div class="spam-chip" [style.background]="spamBg" [style.color]="spamColor">
                  <i class="fas fa-circle" style="font-size:7px"></i>
                  {{ spamResult.rating }} · {{ spamResult.score }}/10
                </div>
              }
              <button class="close-btn" (click)="showEditor = false">
                <i class="fas fa-times"></i>
              </button>
            </div>
          </div>

          <!-- Body: left form | right live preview -->
          <div class="editor-body">

            <!-- ── LEFT PANEL ── -->
            <div class="editor-left">

              <!-- Meta fields -->
              <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
                <div class="form-group" style="margin:0">
                  <label>Name <span style="color:var(--danger)">*</span></label>
                  <input class="form-control" [(ngModel)]="editingTemplate!.name"
                         placeholder="e.g. Welcome Email">
                </div>
                <div class="form-group" style="margin:0">
                  <label>Category</label>
                  <input class="form-control" [(ngModel)]="editingTemplate!.category"
                         list="cat-datalist" placeholder="Newsletter, Promotion...">
                  <datalist id="cat-datalist">
                    @for (c of categories.slice(1); track c) { <option [value]="c"> }
                  </datalist>
                </div>
                <div class="form-group" style="margin:0;grid-column:1/-1">
                  <label>Default Subject Line</label>
                  <input class="form-control" [(ngModel)]="editingTemplate!.subject"
                         placeholder="Your subject line — supports merge tags like {{FirstName}}">
                </div>
              </div>

              <!-- Merge tag toolbar -->
              <div>
                <div style="font-size:11px;font-weight:700;color:var(--gray-500);text-transform:uppercase;letter-spacing:0.6px;margin-bottom:8px">
                  Insert Merge Tag
                </div>
                <div class="merge-tags-bar">
                  @for (mt of MERGE_TAGS; track mt.tag) {
                    <button class="mtag" type="button" (click)="insertMergeTag(mt.tag)" [title]="mt.tag">
                      {{ mt.label }}
                    </button>
                  }
                </div>
              </div>

              <!-- HTML Editor -->
              <div class="form-group" style="margin:0;flex:1;display:flex;flex-direction:column">
                <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px">
                  <label style="margin:0">
                    HTML Content <span style="color:var(--danger)">*</span>
                  </label>
                  <label class="btn btn-sm btn-secondary" style="cursor:pointer;margin:0;font-weight:500">
                    <i class="fas fa-file-import"></i> Import .html
                    <input #htmlFileInput type="file" accept=".html,.htm" style="display:none"
                           (change)="importHtmlFile($event)">
                  </label>
                </div>
                <textarea #htmlTextarea class="form-control html-editor"
                          [(ngModel)]="editingTemplate!.htmlContent"
                          placeholder="Paste or type your HTML email here..."
                          style="flex:1;min-height:280px"></textarea>
              </div>

              <!-- Plain text -->
              <div class="form-group" style="margin:0">
                <label>
                  Plain Text Fallback
                  <span style="font-size:11px;color:var(--gray-400);font-weight:400">(optional — for clients that block HTML)</span>
                </label>
                <textarea class="form-control" style="min-height:72px;font-size:12px;font-family:monospace"
                          [(ngModel)]="editingTemplate!.textContent"
                          placeholder="Plain text version of your email..."></textarea>
              </div>

              <!-- Spam issues list -->
              @if (spamResult?.issues?.length) {
                <div style="background:#FEF3C7;border:1px solid #FDE68A;border-radius:8px;padding:12px 14px">
                  <div style="font-weight:700;font-size:13px;color:#92400E;margin-bottom:8px">
                    <i class="fas fa-exclamation-triangle"></i>
                    {{ spamResult!.issues.length }} issue{{ spamResult!.issues.length > 1 ? 's' : '' }} found
                  </div>
                  @for (issue of spamResult!.issues; track issue.code) {
                    <div style="display:flex;gap:6px;margin-bottom:6px;font-size:12px">
                      <span [style.color]="issueSeverityColor(issue.severity)" style="font-weight:700;flex-shrink:0">
                        {{ issue.severity }}
                      </span>
                      <span style="color:#78350F">{{ issue.message }}</span>
                    </div>
                  }
                </div>
              }

            </div>

            <!-- ── RIGHT PANEL: Live preview ── -->
            <div class="editor-right">
              <div class="device-toggle">
                <button class="device-btn" [class.active]="previewDevice === 'desktop'"
                        (click)="previewDevice = 'desktop'">
                  <i class="fas fa-desktop"></i> Desktop
                </button>
                <button class="device-btn" [class.active]="previewDevice === 'mobile'"
                        (click)="previewDevice = 'mobile'">
                  <i class="fas fa-mobile-alt"></i> Mobile
                </button>
                <button class="device-btn" [class.active]="previewDevice === 'full'"
                        (click)="previewDevice = 'full'">
                  <i class="fas fa-expand-arrows-alt"></i> Full
                </button>
              </div>

              <div class="preview-frame-outer">
                <div class="preview-frame-inner" [style.width]="previewWidth" style="width:100%">
                  <iframe
                    [srcdoc]="editingTemplate?.htmlContent || placeholderHtml"
                    style="width:100%;min-height:520px;height:100%;border:none;display:block">
                  </iframe>
                </div>
              </div>
            </div>

          </div>

          <!-- Footer -->
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showEditor = false">Cancel</button>
            <button class="btn btn-primary" (click)="saveTemplate()" [disabled]="saving">
              @if (saving) { <span class="spinner"></span> }
              Save Template
            </button>
          </div>

        </div>
      </div>
    }


    <!-- ════════════════════════════════════════════════════
         PREVIEW MODAL — Device toggle + Use/Edit actions
         ════════════════════════════════════════════════════ -->
    @if (showPreview) {
      <div class="modal-overlay" (click)="showPreview = false">
        <div class="modal preview-modal" (click)="$event.stopPropagation()">

          <!-- Header -->
          <div class="modal-header" style="align-items:flex-start">
            <div style="min-width:0">
              <h3 style="margin:0 0 2px">{{ previewingTemplate?.name }}</h3>
              @if (previewingTemplate?.subject) {
                <div style="font-size:12px;color:var(--gray-400);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:480px">
                  <i class="fas fa-envelope" style="margin-right:4px"></i>
                  {{ previewingTemplate?.subject }}
                </div>
              }
            </div>
            <div style="display:flex;align-items:center;gap:8px;flex-shrink:0">
              <!-- Device toggle compact -->
              <div style="display:flex;border:1px solid var(--gray-200);border-radius:6px;overflow:hidden">
                <button title="Desktop (600px)"
                        style="padding:6px 12px;border:none;cursor:pointer;font-size:12px;transition:all 0.15s"
                        [style.background]="previewDevice === 'desktop' ? 'var(--primary)' : 'white'"
                        [style.color]="previewDevice === 'desktop' ? 'white' : 'var(--gray-500)'"
                        (click)="previewDevice = 'desktop'">
                  <i class="fas fa-desktop"></i>
                </button>
                <button title="Mobile (390px)"
                        style="padding:6px 12px;border:none;border-left:1px solid var(--gray-200);cursor:pointer;font-size:12px;transition:all 0.15s"
                        [style.background]="previewDevice === 'mobile' ? 'var(--primary)' : 'white'"
                        [style.color]="previewDevice === 'mobile' ? 'white' : 'var(--gray-500)'"
                        (click)="previewDevice = 'mobile'">
                  <i class="fas fa-mobile-alt"></i>
                </button>
                <button title="Full width"
                        style="padding:6px 12px;border:none;border-left:1px solid var(--gray-200);cursor:pointer;font-size:12px;transition:all 0.15s"
                        [style.background]="previewDevice === 'full' ? 'var(--primary)' : 'white'"
                        [style.color]="previewDevice === 'full' ? 'white' : 'var(--gray-500)'"
                        (click)="previewDevice = 'full'">
                  <i class="fas fa-expand-arrows-alt"></i>
                </button>
              </div>
              <button class="close-btn" (click)="showPreview = false">
                <i class="fas fa-times"></i>
              </button>
            </div>
          </div>

          <!-- Preview body -->
          <div class="preview-body">
            <div [style.width]="previewWidth" style="max-width:100%;transition:width 0.3s;box-shadow:0 2px 20px rgba(0,0,0,0.2)">
              <iframe [srcdoc]="previewingTemplate?.htmlContent"
                      style="width:100%;height:620px;border:none;display:block;background:white">
              </iframe>
            </div>
          </div>

          <!-- Footer -->
          <div class="modal-footer" style="justify-content:space-between">
            <div style="display:flex;gap:8px">
              <button class="btn btn-secondary" (click)="showPreview = false">Close</button>
              @if (!previewingTemplate?.isBuiltIn) {
                <button class="btn btn-secondary" (click)="editFromPreview(previewingTemplate!)">
                  <i class="fas fa-pencil-alt"></i> Edit
                </button>
              } @else {
                <button class="btn btn-secondary" (click)="cloneFromPreview(previewingTemplate!)">
                  <i class="fas fa-copy"></i> Clone & Edit
                </button>
              }
            </div>
            <button class="btn btn-primary" (click)="useTemplate(previewingTemplate!, $event)">
              <i class="fas fa-paper-plane"></i> Use in Campaign
            </button>
          </div>

        </div>
      </div>
    }
  `
})
export class TemplatesComponent implements OnInit {

  @ViewChild('htmlTextarea') htmlTextareaRef!: ElementRef<HTMLTextAreaElement>;

  // ── List state ──────────────────────────────────────────────────────────────
  templates: Template[] = [];
  filteredTemplates: Template[] = [];
  categories: string[] = ['All'];
  selectedCategory = 'All';
  searchQuery = '';
  loading = true;

  // ── Editor state ─────────────────────────────────────────────────────────────
  showEditor = false;
  editingTemplate: Partial<Template> | null = null;
  saving = false;
  spamResult: SpamAnalysis | null = null;
  checkingSpam = false;

  // ── Preview state ─────────────────────────────────────────────────────────────
  showPreview = false;
  previewingTemplate: Template | null = null;
  previewDevice: 'desktop' | 'mobile' | 'full' = 'desktop';

  readonly MERGE_TAGS = [
    { label: 'First Name',   tag: '{{FirstName}}'     },
    { label: 'Last Name',    tag: '{{LastName}}'      },
    { label: 'Email',        tag: '{{Email}}'         },
    { label: 'Company',      tag: '{{Company}}'       },
    { label: 'Phone',        tag: '{{Phone}}'         },
    { label: 'Custom 1',     tag: '{{CustomField1}}'  },
    { label: 'Custom 2',     tag: '{{CustomField2}}'  },
    { label: 'Custom 3',     tag: '{{CustomField3}}'  },
    { label: 'Custom 4',     tag: '{{CustomField4}}'  },
    { label: 'Custom 5',     tag: '{{CustomField5}}'  },
    { label: 'Unsubscribe',  tag: '{{UnsubscribeLink}}' },
    { label: 'Year',         tag: '{{CurrentYear}}'   },
  ];

  readonly placeholderHtml = `
    <div style="font-family:sans-serif;padding:60px 40px;text-align:center;color:#9CA3AF">
      <div style="font-size:48px;margin-bottom:16px">✉️</div>
      <div style="font-size:16px;font-weight:600;color:#6B7280;margin-bottom:8px">Live Preview</div>
      <div style="font-size:14px">Start typing HTML on the left to see your email rendered here.</div>
    </div>`;

  private readonly CAT_COLORS = [
    '#3B82F6','#10B981','#F59E0B','#EF4444',
    '#8B5CF6','#06B6D4','#F97316','#14B8A6','#EC4899'
  ];

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit(): void { this.loadTemplates(); }

  // ── Data ────────────────────────────────────────────────────────────────────

  loadTemplates(): void {
    this.loading = true;
    this.api.getTemplates().subscribe({
      next: (t) => {
        this.templates = t;
        this.deriveCategories();
        this.applyFilters();
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  deriveCategories(): void {
    const cats = [...new Set(
      this.templates.map(t => t.category).filter(Boolean) as string[]
    )].sort();
    this.categories = ['All', ...cats];
  }

  applyFilters(): void {
    let result = this.templates;
    if (this.selectedCategory !== 'All') {
      result = result.filter(t => t.category === this.selectedCategory);
    }
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(t =>
        t.name.toLowerCase().includes(q) ||
        (t.subject?.toLowerCase().includes(q)) ||
        (t.category?.toLowerCase().includes(q))
      );
    }
    this.filteredTemplates = result;
  }

  filterByCategory(cat: string): void {
    this.selectedCategory = cat;
    this.applyFilters();
  }

  // ── Card actions ─────────────────────────────────────────────────────────────

  useTemplate(t: Template, event: Event): void {
    event.stopPropagation();
    localStorage.setItem('selectedTemplate', JSON.stringify(t));
    this.toast.info('Template selected', 'Go to Campaigns → New Campaign to use it');
  }

  cloneTemplate(t: Template, event: Event): void {
    event.stopPropagation();
    const clone: Partial<Template> = {
      name: `Copy of ${t.name}`,
      category: t.category,
      subject: t.subject,
      description: t.description,
      htmlContent: t.htmlContent,
      textContent: t.textContent,
      isBuiltIn: false
    };
    this.api.createTemplate(clone).subscribe({
      next: () => {
        this.toast.success('Duplicated', `"Copy of ${t.name}" created`);
        this.loadTemplates();
      },
      error: () => this.toast.error('Failed to duplicate template')
    });
  }

  previewTemplate(t: Template, event?: Event): void {
    event?.stopPropagation();
    this.previewingTemplate = t;
    this.previewDevice = 'desktop';
    this.showPreview = true;
  }

  editTemplate(t: Template, event?: Event): void {
    event?.stopPropagation();
    this.editingTemplate = { ...t };
    this.spamResult = null;
    this.showEditor = true;
    this.showPreview = false;
  }

  deleteTemplate(t: Template, event: Event): void {
    event.stopPropagation();
    if (!confirm(`Delete "${t.name}"? This cannot be undone.`)) return;
    this.api.deleteTemplate(t.id!).subscribe({
      next: () => { this.toast.success('Deleted'); this.loadTemplates(); },
      error: () => this.toast.error('Failed to delete')
    });
  }

  // ── Preview modal helpers ─────────────────────────────────────────────────────

  editFromPreview(t: Template): void {
    this.showPreview = false;
    this.editTemplate(t);
  }

  cloneFromPreview(t: Template): void {
    this.showPreview = false;
    const clone: Partial<Template> = {
      name: `Copy of ${t.name}`,
      category: t.category, subject: t.subject,
      description: t.description,
      htmlContent: t.htmlContent, textContent: t.textContent,
      isBuiltIn: false
    };
    this.api.createTemplate(clone).subscribe({
      next: (saved) => {
        this.toast.success('Cloned', 'Opening editor...');
        this.loadTemplates();
        this.editTemplate(saved as Template);
      },
      error: () => this.toast.error('Failed to clone')
    });
  }

  // ── Editor ──────────────────────────────────────────────────────────────────

  openNew(): void {
    this.editingTemplate = { name: '', category: '', subject: '', htmlContent: '', textContent: '' };
    this.spamResult = null;
    this.showEditor = true;
  }

  saveTemplate(): void {
    if (!this.editingTemplate?.name?.trim()) {
      this.toast.error('Template name is required');
      return;
    }
    if (!this.editingTemplate?.htmlContent?.trim()) {
      this.toast.error('HTML content is required');
      return;
    }
    this.saving = true;
    const obs = this.editingTemplate.id
      ? this.api.updateTemplate(this.editingTemplate.id, this.editingTemplate)
      : this.api.createTemplate(this.editingTemplate);
    obs.subscribe({
      next: () => {
        this.toast.success('Template saved');
        this.showEditor = false;
        this.saving = false;
        this.spamResult = null;
        this.loadTemplates();
      },
      error: () => { this.saving = false; this.toast.error('Failed to save template'); }
    });
  }

  /** Inserts a merge tag at the current cursor position in the HTML textarea. */
  insertMergeTag(tag: string): void {
    const el = this.htmlTextareaRef?.nativeElement;
    if (!this.editingTemplate) return;
    if (!el) {
      this.editingTemplate.htmlContent = (this.editingTemplate.htmlContent || '') + tag;
      return;
    }
    const start = el.selectionStart ?? 0;
    const end   = el.selectionEnd   ?? 0;
    const html  = this.editingTemplate.htmlContent || '';
    this.editingTemplate.htmlContent = html.slice(0, start) + tag + html.slice(end);
    setTimeout(() => {
      el.selectionStart = el.selectionEnd = start + tag.length;
      el.focus();
    });
  }

  /** Reads an uploaded .html file into the HTML content field. */
  importHtmlFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file || !this.editingTemplate) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      this.editingTemplate!.htmlContent = e.target?.result as string;
      this.toast.success('Imported', file.name);
    };
    reader.readAsText(file);
    (event.target as HTMLInputElement).value = '';
  }

  checkSpamScore(): void {
    if (!this.editingTemplate?.htmlContent) return;
    this.checkingSpam = true;
    this.spamResult = null;
    this.api.analyzeSpam(
      this.editingTemplate.subject || '(no subject)',
      this.editingTemplate.htmlContent
    ).subscribe({
      next: (r) => { this.spamResult = r; this.checkingSpam = false; },
      error: () => { this.checkingSpam = false; this.toast.error('Spam check failed'); }
    });
  }

  // ── Computed helpers ─────────────────────────────────────────────────────────

  get previewWidth(): string {
    return { desktop: '620px', mobile: '390px', full: '100%' }[this.previewDevice];
  }

  get spamColor(): string {
    return { GOOD: '#065F46', WARNING: '#92400E', POOR: '#991B1B' }[this.spamResult?.rating ?? 'GOOD'] ?? '';
  }

  get spamBg(): string {
    return { GOOD: '#D1FAE5', WARNING: '#FEF3C7', POOR: '#FEE2E2' }[this.spamResult?.rating ?? 'GOOD'] ?? '';
  }

  issueSeverityColor(sev: string): string {
    return { CRITICAL: '#DC2626', HIGH: '#EA580C', MEDIUM: '#D97706', LOW: '#6B7280' }[sev] ?? '#6B7280';
  }

  /** Deterministic color per category name (consistent across page loads). */
  getCategoryColor(cat: string): string {
    let hash = 0;
    for (let i = 0; i < cat.length; i++) hash = cat.charCodeAt(i) + hash * 31;
    return this.CAT_COLORS[Math.abs(hash) % this.CAT_COLORS.length];
  }
}
