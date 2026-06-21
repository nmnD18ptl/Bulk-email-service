import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { Contact, Tag, PageResponse, ImportPreview, ImportResult } from '../../models/models';

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    .toolbar {
      display: flex; align-items: center; gap: 12px;
      margin-bottom: 20px; flex-wrap: wrap;
    }
    .filter-select {
      padding: 8px 12px;
      border: 1px solid var(--gray-300);
      border-radius: 6px; font-size: 14px;
      background: white; color: var(--gray-700);
      outline: none; cursor: pointer;
    }
    .import-zone {
      border: 2px dashed var(--gray-300);
      border-radius: 10px; padding: 40px;
      text-align: center; cursor: pointer;
      transition: all 0.2s;
      background: var(--gray-50);
      &:hover, &.drag-over { border-color: var(--primary); background: #EFF6FF; }
      i { font-size: 32px; color: var(--gray-400); margin-bottom: 12px; }
      p { color: var(--gray-500); font-size: 14px; }
      strong { color: var(--primary); }
    }
    .stats-row {
      display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap;
      .stat-pill {
        padding: 6px 14px; border-radius: 100px;
        font-size: 13px; font-weight: 500; cursor: pointer;
        transition: all 0.15s;
        &.all { background: #EFF6FF; color: var(--primary); }
        &.active { background: #ECFDF5; color: #065F46; }
        &.unsubscribed { background: #FFFBEB; color: #92400E; }
        &.bounced { background: #FEF2F2; color: #991B1B; }
        &.selected { outline: 2px solid currentColor; }
      }
    }
    .checkbox-cell { width: 40px; }
    .contact-email { font-weight: 500; color: var(--gray-800); }
    .contact-name { color: var(--gray-500); font-size: 13px; }
    .bulk-actions {
      display: flex; align-items: center; gap: 10px;
      background: #EFF6FF; padding: 10px 16px;
      border-radius: 8px; margin-bottom: 12px;
      span { font-size: 14px; color: var(--primary); font-weight: 500; }
    }

    /* ── Import wizard ── */
    .import-steps {
      display: flex; align-items: center; gap: 6px;
      padding: 16px 24px 0;
    }
    .import-step-item {
      display: flex; align-items: center; gap: 6px;
      font-size: 13px; font-weight: 500; color: var(--gray-400);
      .step-num {
        width: 22px; height: 22px; border-radius: 50%;
        background: var(--gray-200); color: var(--gray-500);
        display: flex; align-items: center; justify-content: center;
        font-size: 11px; font-weight: 700; flex-shrink: 0;
      }
      &.active { color: var(--primary); .step-num { background: var(--primary); color: white; } }
      &.done   { color: var(--success); .step-num { background: var(--success); color: white; } }
    }
    .step-sep { color: var(--gray-300); font-size: 14px; }

    .preview-table-wrap {
      overflow-x: auto;
      border: 1px solid var(--gray-200); border-radius: 8px;
      margin-bottom: 20px;
    }
    .preview-table {
      width: 100%; border-collapse: collapse; font-size: 12px; min-width: 300px;
      th {
        padding: 8px 12px; text-align: left; white-space: nowrap;
        background: var(--gray-100); border-bottom: 1px solid var(--gray-200);
        color: var(--gray-600); font-weight: 600;
      }
      td {
        padding: 6px 12px; color: var(--gray-700);
        max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        border-bottom: 1px solid var(--gray-100);
      }
    }

    .map-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .map-row {
      display: flex; align-items: center; gap: 10px;
      padding: 10px 12px; background: var(--gray-50);
      border-radius: 6px; border: 1px solid var(--gray-200);
      &.required-missing { border-color: var(--danger); }
    }
    .map-label { flex: 1; font-size: 13px; font-weight: 500; color: var(--gray-700); }
    .map-select {
      padding: 6px 8px; border: 1px solid var(--gray-300);
      border-radius: 6px; font-size: 13px; background: white;
      min-width: 160px; max-width: 200px; outline: none;
    }

    .result-grid {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px;
      max-width: 480px; margin: 0 auto 24px;
    }
    .result-card {
      background: var(--gray-50); border-radius: 8px; padding: 16px; text-align: center;
      .result-num { font-size: 28px; font-weight: 700; }
      .result-label { font-size: 12px; color: var(--gray-500); margin-top: 4px; }
    }
  `],
  template: `
    <div class="page-content">
      <div class="page-header">
        <div>
          <div class="page-title">Contacts</div>
          <div class="page-subtitle">Manage your email subscribers</div>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" (click)="exportContacts()">
            <i class="fas fa-download"></i> Export
          </button>
          <button class="btn btn-primary" (click)="openImportModal()">
            <i class="fas fa-upload"></i> Import
          </button>
          <button class="btn btn-primary" (click)="openAddModal()">
            <i class="fas fa-plus"></i> Add Contact
          </button>
        </div>
      </div>

      <!-- Status pills -->
      <div class="stats-row">
        @for (pill of statusPills; track pill.key) {
          <div class="stat-pill {{ pill.class }}" [class.selected]="selectedStatus === pill.key"
               (click)="filterByStatus(pill.key)">
            {{ pill.label }}: {{ contactStats[pill.key] || 0 }}
          </div>
        }
      </div>

      <!-- Toolbar -->
      <div class="toolbar">
        <div class="search-bar" style="width:300px">
          <i class="fas fa-search"></i>
          <input placeholder="Search by email, name, company..." [(ngModel)]="searchQuery"
                 (input)="onSearch()">
        </div>
        <select class="filter-select" [(ngModel)]="selectedStatus" (change)="loadContacts()">
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="UNSUBSCRIBED">Unsubscribed</option>
          <option value="BOUNCED">Bounced</option>
          <option value="COMPLAINED">Complained</option>
          <option value="INVALID">Invalid</option>
        </select>
        <div style="margin-left:auto;font-size:14px;color:var(--gray-500)">
          {{ page.totalElements | number }} contacts
        </div>
      </div>

      <!-- Bulk actions -->
      @if (selectedIds.size > 0) {
        <div class="bulk-actions">
          <span>{{ selectedIds.size }} selected</span>
          <button class="btn btn-sm btn-danger" (click)="bulkDelete()">
            <i class="fas fa-trash"></i> Delete
          </button>
          <button class="btn btn-sm btn-secondary" (click)="selectedIds.clear()">
            <i class="fas fa-times"></i> Clear
          </button>
        </div>
      }

      <!-- Table -->
      <div class="card">
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th class="checkbox-cell">
                  <input type="checkbox" (change)="toggleSelectAll($event)">
                </th>
                <th>Email</th>
                <th>Name</th>
                <th>Company</th>
                <th>Country</th>
                <th>Status</th>
                <th>Score</th>
                <th>Added</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @if (loading) {
                <tr><td colspan="9" style="text-align:center;padding:40px">
                  <div class="spinner spinner-dark"></div>
                </td></tr>
              } @else if (contacts.length === 0) {
                <tr><td colspan="9">
                  <div class="empty-state">
                    <i class="fas fa-users"></i>
                    <h3>No contacts found</h3>
                    <p>Import contacts from CSV/Excel or add them manually</p>
                  </div>
                </td></tr>
              } @else {
                @for (contact of contacts; track contact.id) {
                  <tr>
                    <td><input type="checkbox" [checked]="selectedIds.has(contact.id!)"
                               (change)="toggleSelect(contact.id!)"></td>
                    <td>
                      <div class="contact-email">{{ contact.email }}</div>
                      @if (contact.tags?.length) {
                        <div style="display:flex;gap:4px;margin-top:3px;flex-wrap:wrap">
                          @for (tag of contact.tags!.slice(0,3); track tag.id) {
                            <span style="font-size:10px;padding:1px 6px;border-radius:10px;color:white"
                                  [style.background]="tag.color || '#3B82F6'">{{ tag.name }}</span>
                          }
                        </div>
                      }
                    </td>
                    <td class="contact-name">{{ contact.firstName }} {{ contact.lastName }}</td>
                    <td>{{ contact.company || '-' }}</td>
                    <td>{{ contact.country || '-' }}</td>
                    <td>
                      <span class="badge" [class]="getStatusBadge(contact.status)">{{ contact.status }}</span>
                    </td>
                    <td>
                      <div class="progress-bar" style="width:60px;margin-bottom:2px">
                        <div class="progress-fill" [class]="contact.engagementScore! >= 50 ? 'success' : ''"
                             [style.width.%]="Math.min(contact.engagementScore || 0, 100)"></div>
                      </div>
                      <span style="font-size:11px;color:var(--gray-500)">{{ contact.engagementScore || 0 }}</span>
                    </td>
                    <td style="font-size:12px;color:var(--gray-400)">{{ contact.createdAt | date:'MMM d, y' }}</td>
                    <td>
                      <div style="display:flex;gap:4px">
                        <button class="btn btn-icon" (click)="editContact(contact)" title="Edit">
                          <i class="fas fa-pencil-alt"></i>
                        </button>
                        <button class="btn btn-icon" style="color:var(--danger)" (click)="deleteContact(contact)" title="Delete">
                          <i class="fas fa-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        @if (page.totalPages > 1) {
          <div class="pagination">
            <button (click)="changePage(currentPage - 1)" [disabled]="page.first">
              <i class="fas fa-chevron-left"></i>
            </button>
            @for (p of getPageNumbers(); track p) {
              <button (click)="changePage(p)" [class.active]="p === currentPage">{{ p + 1 }}</button>
            }
            <button (click)="changePage(currentPage + 1)" [disabled]="page.last">
              <i class="fas fa-chevron-right"></i>
            </button>
          </div>
        }
      </div>
    </div>

    <!-- ═══════════════════════════════════════════════════════
         Import Wizard Modal  (3-step: Upload → Map → Result)
         ═══════════════════════════════════════════════════════ -->
    @if (showImportModal) {
      <div class="modal-overlay" (click)="closeImportModal()">
        <div class="modal" style="max-width:680px" (click)="$event.stopPropagation()">

          <!-- Header -->
          <div class="modal-header">
            <h3>Import Contacts</h3>
            <button class="close-btn" (click)="closeImportModal()">
              <i class="fas fa-times"></i>
            </button>
          </div>

          <!-- Step indicator -->
          <div class="import-steps">
            <div class="import-step-item"
                 [class.active]="importStep === 'upload'"
                 [class.done]="importStep === 'map' || importStep === 'result'">
              <span class="step-num">
                @if (importStep === 'map' || importStep === 'result') { <i class="fas fa-check" style="font-size:10px"></i> }
                @else { 1 }
              </span>
              Upload
            </div>
            <span class="step-sep">›</span>
            <div class="import-step-item"
                 [class.active]="importStep === 'map'"
                 [class.done]="importStep === 'result'">
              <span class="step-num">
                @if (importStep === 'result') { <i class="fas fa-check" style="font-size:10px"></i> }
                @else { 2 }
              </span>
              Map Columns
            </div>
            <span class="step-sep">›</span>
            <div class="import-step-item" [class.active]="importStep === 'result'">
              <span class="step-num">3</span>
              Result
            </div>
          </div>

          <!-- ── STEP 1: Upload ── -->
          @if (importStep === 'upload') {
            <div class="modal-body">
              <div class="import-zone" [class.drag-over]="isDragging"
                   (dragover)="$event.preventDefault(); isDragging = true"
                   (dragleave)="isDragging = false"
                   (drop)="onFileDrop($event)"
                   (click)="fileInput.click()">
                <i class="fas fa-cloud-upload-alt"></i>
                <p>Drop your <strong>CSV or Excel (.xlsx)</strong> file here</p>
                <p style="margin-top:8px">or click to browse</p>
                <input #fileInput type="file" accept=".csv,.xlsx" style="display:none"
                       (change)="onFileSelect($event)">
              </div>

              @if (importFile) {
                <div class="alert alert-info" style="margin-top:16px">
                  <i class="fas fa-file-excel"></i>
                  <span>{{ importFile.name }}
                    <span style="color:var(--gray-400);margin-left:6px">({{ (importFile.size / 1024) | number:'1.0-0' }} KB)</span>
                  </span>
                </div>
              }

              <div class="form-group" style="margin-top:16px">
                <label>Add tags to imported contacts (optional)</label>
                <input class="form-control" placeholder="newsletter, vip, 2024 (comma separated)"
                       [(ngModel)]="importTagsInput">
              </div>

              <div class="alert alert-info" style="margin-top:4px">
                <i class="fas fa-info-circle"></i>
                <div>
                  Any column layout works. You will map columns to fields on the next step.<br>
                  Duplicates, unsubscribed, and bounced contacts are skipped automatically.
                </div>
              </div>
            </div>

            <div class="modal-footer">
              <button class="btn btn-secondary" (click)="closeImportModal()">Cancel</button>
              <button class="btn btn-primary" (click)="previewFile()" [disabled]="!importFile || previewing">
                @if (previewing) { <span class="spinner"></span> }
                {{ previewing ? 'Reading file...' : 'Next: Map Columns' }}
                @if (!previewing) { <i class="fas fa-arrow-right" style="margin-left:6px"></i> }
              </button>
            </div>
          }

          <!-- ── STEP 2: Map Columns ── -->
          @if (importStep === 'map') {
            <div class="modal-body">

              <!-- File summary -->
              <div style="background:var(--gray-50);border-radius:8px;padding:12px 16px;margin-bottom:20px;display:flex;align-items:center;gap:16px">
                <i class="fas fa-file-excel" style="color:var(--success);font-size:20px"></i>
                <div>
                  <div style="font-weight:600;font-size:14px">{{ importFile?.name }}</div>
                  <div style="font-size:12px;color:var(--gray-500);margin-top:2px">
                    {{ importPreview?.headers?.length }} columns detected &nbsp;·&nbsp;
                    {{ importPreview?.totalRows | number }} data rows
                  </div>
                </div>
              </div>

              <!-- Data preview table -->
              <div style="font-weight:600;font-size:13px;color:var(--gray-600);margin-bottom:8px">
                Preview (first 3 rows of your file)
              </div>
              <div class="preview-table-wrap">
                <table class="preview-table">
                  <thead>
                    <tr>
                      @for (h of importPreview?.headers; track $index) {
                        <th>{{ getColLabel($index) }}: {{ h }}</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of importPreview?.sampleRows?.slice(0, 3); track $index) {
                      <tr>
                        @for (cell of row; track $index) {
                          <td>{{ cell || '—' }}</td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>

              <!-- Mapping form -->
              <div style="font-weight:600;font-size:14px;margin-bottom:12px">
                Tell us which column is which
              </div>
              <div class="map-grid">
                @for (field of CONTACT_FIELDS; track field.key) {
                  <div class="map-row" [class.required-missing]="field.required && columnMapping[field.key] === null">
                    <div class="map-label">
                      {{ field.label }}
                      @if (field.required) { <span style="color:var(--danger)"> *</span> }
                    </div>
                    <select class="map-select" (change)="onMappingChange(field.key, $event)">
                      <option value="" [selected]="columnMapping[field.key] === null">
                        — Don't import —
                      </option>
                      @for (h of importPreview?.headers; track $index) {
                        <option [value]="$index" [selected]="columnMapping[field.key] === $index">
                          {{ getColLabel($index) }}: {{ h }}
                        </option>
                      }
                    </select>
                  </div>
                }
              </div>

              @if (!mappingValid()) {
                <div style="color:var(--danger);font-size:13px;margin-top:14px;display:flex;align-items:center;gap:6px">
                  <i class="fas fa-exclamation-circle"></i>
                  Please select which column contains email addresses.
                </div>
              }
            </div>

            <div class="modal-footer">
              <button class="btn btn-secondary" (click)="importStep = 'upload'">
                <i class="fas fa-arrow-left" style="margin-right:6px"></i> Back
              </button>
              <button class="btn btn-primary" (click)="doImportWithMapping()"
                      [disabled]="!mappingValid() || importing">
                @if (importing) { <span class="spinner"></span> }
                {{ importing ? 'Importing...' : 'Import Contacts' }}
              </button>
            </div>
          }

          <!-- ── STEP 3: Result ── -->
          @if (importStep === 'result') {
            <div class="modal-body" style="text-align:center;padding-top:32px;padding-bottom:32px">
              <div style="font-size:52px;margin-bottom:16px">✅</div>
              <h3 style="color:var(--gray-800);margin-bottom:24px">Import Complete</h3>

              <div class="result-grid">
                <div class="result-card">
                  <div class="result-num" style="color:var(--primary)">{{ importResult?.imported | number }}</div>
                  <div class="result-label">Imported</div>
                </div>
                <div class="result-card">
                  <div class="result-num" style="color:var(--gray-500)">{{ importResult?.duplicates | number }}</div>
                  <div class="result-label">Duplicates</div>
                </div>
                <div class="result-card">
                  <div class="result-num" style="color:var(--warning)">{{ importResult?.suppressed | number }}</div>
                  <div class="result-label">Suppressed</div>
                </div>
                <div class="result-card">
                  <div class="result-num" style="color:var(--danger)">{{ importResult?.invalid | number }}</div>
                  <div class="result-label">Invalid</div>
                </div>
              </div>

              @if ((importResult?.imported ?? 0) === 0) {
                <div class="alert alert-warning" style="max-width:440px;margin:0 auto">
                  <i class="fas fa-exclamation-triangle"></i>
                  <div>
                    No contacts were imported.
                    @if ((importResult?.invalid ?? 0) > 0) {
                      Check that the Email column contains valid email addresses.
                    } @else if ((importResult?.duplicates ?? 0) > 0) {
                      All contacts already exist in your list.
                    } @else {
                      Please go back and verify your column mapping.
                    }
                  </div>
                </div>
              }
            </div>

            <div class="modal-footer">
              @if ((importResult?.imported ?? 0) === 0) {
                <button class="btn btn-secondary" (click)="importStep = 'map'">
                  <i class="fas fa-arrow-left" style="margin-right:6px"></i> Fix Mapping
                </button>
              }
              <button class="btn btn-primary" (click)="closeImportModal()">Done</button>
            </div>
          }

        </div>
      </div>
    }

    <!-- Add/Edit Modal — unchanged -->
    @if (showEditModal) {
      <div class="modal-overlay" (click)="showEditModal = false">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ editingContact?.id ? 'Edit Contact' : 'Add Contact' }}</h3>
            <button class="close-btn" (click)="showEditModal = false">
              <i class="fas fa-times"></i>
            </button>
          </div>
          <div class="modal-body" style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
            <div class="form-group" style="grid-column:1/-1">
              <label>Email *</label>
              <input class="form-control" [(ngModel)]="editingContact!.email" placeholder="email@example.com">
            </div>
            <div class="form-group">
              <label>First Name</label>
              <input class="form-control" [(ngModel)]="editingContact!.firstName">
            </div>
            <div class="form-group">
              <label>Last Name</label>
              <input class="form-control" [(ngModel)]="editingContact!.lastName">
            </div>
            <div class="form-group">
              <label>Company</label>
              <input class="form-control" [(ngModel)]="editingContact!.company">
            </div>
            <div class="form-group">
              <label>Country</label>
              <input class="form-control" [(ngModel)]="editingContact!.country">
            </div>
            <div class="form-group">
              <label>Phone</label>
              <input class="form-control" [(ngModel)]="editingContact!.phone">
            </div>
            <div class="form-group">
              <label>Status</label>
              <select class="form-control" [(ngModel)]="editingContact!.status">
                <option value="ACTIVE">Active</option>
                <option value="UNSUBSCRIBED">Unsubscribed</option>
                <option value="BOUNCED">Bounced</option>
              </select>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showEditModal = false">Cancel</button>
            <button class="btn btn-primary" (click)="saveContact()" [disabled]="saving">
              @if (saving) { <span class="spinner"></span> }
              Save Contact
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ContactsComponent implements OnInit {

  // ── Contact list state (unchanged) ──────────────────────────────────────────
  contacts: Contact[] = [];
  page: PageResponse<Contact> = {
    content: [], totalElements: 0, totalPages: 0,
    size: 50, number: 0, first: true, last: true
  };
  loading = false;
  currentPage = 0;
  searchQuery = '';
  selectedStatus = '';
  selectedIds = new Set<number>();
  contactStats: Record<string, number> = {};
  Math = Math;

  // ── Edit modal state (unchanged) ────────────────────────────────────────────
  showEditModal = false;
  editingContact: Partial<Contact> | null = null;
  saving = false;

  // ── Import wizard state ──────────────────────────────────────────────────────
  showImportModal = false;
  importStep: 'upload' | 'map' | 'result' = 'upload';
  importFile: File | null = null;
  importTagsInput = '';
  isDragging = false;
  previewing = false;
  importing = false;
  importPreview: ImportPreview | null = null;
  importResult: ImportResult | null = null;
  /** field key → 0-based column index in the source file, or null = don't import */
  columnMapping: Record<string, number | null> = {};

  // ── Field definitions for the mapping UI ────────────────────────────────────
  readonly CONTACT_FIELDS = [
    { key: 'email',        label: 'Email',          required: true  },
    { key: 'firstName',    label: 'First Name',      required: false },
    { key: 'lastName',     label: 'Last Name',       required: false },
    { key: 'company',      label: 'Company',         required: false },
    { key: 'country',      label: 'Country / State', required: false },
    { key: 'phone',        label: 'Phone',           required: false },
    { key: 'customField1', label: 'Custom Field 1',  required: false },
    { key: 'customField2', label: 'Custom Field 2',  required: false },
    { key: 'customField3', label: 'Custom Field 3',  required: false },
    { key: 'customField4', label: 'Custom Field 4',  required: false },
    { key: 'customField5', label: 'Custom Field 5',  required: false },
  ];

  statusPills = [
    { key: 'total',       label: 'Total',        class: 'all' },
    { key: 'active',      label: 'Active',       class: 'active' },
    { key: 'unsubscribed',label: 'Unsubscribed', class: 'unsubscribed' },
    { key: 'bounced',     label: 'Bounced',      class: 'bounced' }
  ];

  private searchTimer: any;

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit(): void {
    this.loadContacts();
    this.loadStats();
  }

  // ── Contact list methods (all unchanged) ────────────────────────────────────

  loadContacts(): void {
    this.loading = true;
    const params: any = { page: this.currentPage, size: 50 };
    if (this.searchQuery) params.search = this.searchQuery;
    if (this.selectedStatus) params.status = this.selectedStatus;

    this.api.getContacts(params).subscribe({
      next: (data) => { this.page = data; this.contacts = data.content; this.loading = false; },
      error: () => this.loading = false
    });
  }

  loadStats(): void {
    this.api.getContactStats().subscribe(stats => this.contactStats = stats);
  }

  onSearch(): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => { this.currentPage = 0; this.loadContacts(); }, 400);
  }

  filterByStatus(key: string): void {
    this.selectedStatus = key === 'total' ? '' : key.toUpperCase();
    this.currentPage = 0;
    this.loadContacts();
  }

  changePage(p: number): void {
    this.currentPage = p;
    this.loadContacts();
  }

  getPageNumbers(): number[] {
    const total = this.page.totalPages;
    const curr = this.currentPage;
    const pages = [];
    for (let i = Math.max(0, curr - 2); i < Math.min(total, curr + 3); i++) {
      pages.push(i);
    }
    return pages;
  }

  toggleSelect(id: number): void {
    if (this.selectedIds.has(id)) this.selectedIds.delete(id);
    else this.selectedIds.add(id);
  }

  toggleSelectAll(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked) this.contacts.forEach(c => this.selectedIds.add(c.id!));
    else this.selectedIds.clear();
  }

  bulkDelete(): void {
    if (!confirm(`Delete ${this.selectedIds.size} contacts?`)) return;
    const ids = Array.from(this.selectedIds);
    Promise.all(ids.map(id => this.api.deleteContact(id).toPromise())).then(() => {
      this.toast.success('Deleted', `${ids.length} contacts removed`);
      this.selectedIds.clear();
      this.loadContacts();
      this.loadStats();
    });
  }

  openAddModal(): void {
    this.editingContact = { email: '', status: 'ACTIVE' };
    this.showEditModal = true;
  }

  editContact(contact: Contact): void {
    this.editingContact = { ...contact };
    this.showEditModal = true;
  }

  saveContact(): void {
    if (!this.editingContact?.email) { this.toast.error('Email is required'); return; }
    this.saving = true;
    const obs = this.editingContact.id
      ? this.api.updateContact(this.editingContact.id, this.editingContact)
      : this.api.createContact(this.editingContact);
    obs.subscribe({
      next: () => {
        this.toast.success('Saved', 'Contact saved successfully');
        this.showEditModal = false;
        this.saving = false;
        this.loadContacts();
        this.loadStats();
      },
      error: () => { this.saving = false; this.toast.error('Failed to save contact'); }
    });
  }

  deleteContact(contact: Contact): void {
    if (!confirm(`Delete ${contact.email}?`)) return;
    this.api.deleteContact(contact.id!).subscribe(() => {
      this.toast.success('Deleted');
      this.loadContacts();
      this.loadStats();
    });
  }

  exportContacts(): void {
    this.api.exportContacts(this.selectedStatus || undefined).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'contacts.csv'; a.click();
      URL.revokeObjectURL(url);
    });
  }

  getStatusBadge(status: string): string {
    const map: Record<string, string> = {
      'ACTIVE': 'badge-success', 'UNSUBSCRIBED': 'badge-warning',
      'BOUNCED': 'badge-danger', 'COMPLAINED': 'badge-danger', 'INVALID': 'badge-gray'
    };
    return map[status] || 'badge-gray';
  }

  // ── Import wizard methods ────────────────────────────────────────────────────

  openImportModal(): void {
    this.showImportModal = true;
  }

  closeImportModal(): void {
    this.showImportModal = false;
    this.importStep = 'upload';
    this.importFile = null;
    this.importPreview = null;
    this.columnMapping = {};
    this.importResult = null;
    this.importTagsInput = '';
    this.previewing = false;
    this.importing = false;
    this.isDragging = false;
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    const file = event.dataTransfer?.files[0];
    if (file) this.importFile = file;
  }

  onFileSelect(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.importFile = file;
  }

  /** Step 1 → 2: call preview endpoint, then auto-suggest mappings. */
  previewFile(): void {
    if (!this.importFile) return;
    this.previewing = true;
    this.api.previewImport(this.importFile).subscribe({
      next: (preview) => {
        this.importPreview = preview;
        this.columnMapping = this.autoSuggest(preview.headers);
        this.importStep = 'map';
        this.previewing = false;
      },
      error: (e) => {
        this.previewing = false;
        this.toast.error('Could not read file', e.error?.error || 'Please check the file format and try again.');
      }
    });
  }

  /** Step 2 → 3: import using the mapping the user confirmed. */
  doImportWithMapping(): void {
    if (!this.importFile || !this.mappingValid()) return;
    this.importing = true;

    const tags = this.importTagsInput
      ? this.importTagsInput.split(',').map(t => t.trim()).filter(Boolean)
      : [];

    // Strip null entries — backend only needs columns that are actually mapped
    const mapping: Record<string, number> = {};
    Object.entries(this.columnMapping).forEach(([k, v]) => {
      if (v !== null && v !== undefined) mapping[k] = v;
    });

    this.api.importContacts(this.importFile, tags, mapping).subscribe({
      next: (result) => {
        this.importResult = result;
        this.importStep = 'result';
        this.importing = false;
        this.loadContacts();
        this.loadStats();
      },
      error: (e) => {
        this.importing = false;
        this.toast.error('Import failed', e.error?.error || 'Please try again.');
      }
    });
  }

  /** Returns true only when the required Email field is mapped. */
  mappingValid(): boolean {
    return this.columnMapping['email'] !== null && this.columnMapping['email'] !== undefined;
  }

  /** Fires when the user changes a mapping dropdown. */
  onMappingChange(fieldKey: string, event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    this.columnMapping[fieldKey] = val === '' ? null : parseInt(val, 10);
  }

  /**
   * Converts a 0-based column index to a spreadsheet-style letter label.
   * 0 → A, 1 → B, … 25 → Z, 26 → AA, etc.
   */
  getColLabel(index: number): string {
    let label = '';
    let n = index;
    do {
      label = String.fromCharCode(65 + (n % 26)) + label;
      n = Math.floor(n / 26) - 1;
    } while (n >= 0);
    return label;
  }

  /**
   * Scans detected header names and returns an initial mapping suggestion.
   * Normalises headers to lowercase alphanumeric before matching so
   * "Email Address", "EMAIL", "e-mail", "Email ID" all resolve to the
   * email field automatically.
   */
  private autoSuggest(headers: string[]): Record<string, number | null> {
    const mapping: Record<string, number | null> = {};
    this.CONTACT_FIELDS.forEach(f => (mapping[f.key] = null));

    headers.forEach((h, i) => {
      const n = h.toLowerCase().replace(/[^a-z0-9]/g, ''); // normalised

      if (mapping['email'] === null &&
          (n === 'email' || n.includes('email') || n === 'mail' || n === 'emailid' || n === 'emails')) {
        mapping['email'] = i;
      } else if (mapping['firstName'] === null &&
                 (n === 'firstname' || n === 'fname' || n === 'givenname' || n === 'name')) {
        mapping['firstName'] = i;
      } else if (mapping['lastName'] === null &&
                 (n === 'lastname' || n === 'lname' || n === 'surname' || n === 'familyname')) {
        mapping['lastName'] = i;
      } else if (mapping['company'] === null &&
                 (n === 'company' || n === 'companyname' || n === 'organization' ||
                  n === 'organisation' || n === 'business' || n === 'org')) {
        mapping['company'] = i;
      } else if (mapping['country'] === null &&
                 (n === 'country' || n === 'state' || n === 'region' || n === 'location')) {
        mapping['country'] = i;
      } else if (mapping['phone'] === null &&
                 (n === 'phone' || n === 'mobile' || n === 'telephone' || n === 'tel' ||
                  n === 'mob' || n === 'phonenumber' || n === 'mobilenumber' || n === 'contact')) {
        mapping['phone'] = i;
      }
    });

    return mapping;
  }
}
