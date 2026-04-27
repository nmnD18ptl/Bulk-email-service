import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { ToastService } from '../../services/toast.service';
import { Contact, Tag, PageResponse } from '../../models/models';

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
          <button class="btn btn-primary" (click)="showImportModal = true">
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

    <!-- Import Modal -->
    @if (showImportModal) {
      <div class="modal-overlay" (click)="showImportModal = false">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>Import Contacts</h3>
            <button class="close-btn" (click)="showImportModal = false">
              <i class="fas fa-times"></i>
            </button>
          </div>
          <div class="modal-body">
            <div class="import-zone" [class.drag-over]="isDragging"
                 (dragover)="$event.preventDefault(); isDragging=true"
                 (dragleave)="isDragging=false"
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
                <i class="fas fa-file"></i>
                <span>{{ importFile.name }} ({{ (importFile.size / 1024) | number:'1.0-0' }} KB)</span>
              </div>
            }
            <div class="form-group" style="margin-top:16px">
              <label>Add tags to imported contacts (optional)</label>
              <input class="form-control" placeholder="tag1, tag2, tag3 (comma separated)"
                     [(ngModel)]="importTagsInput">
            </div>
            <div class="alert alert-info">
              <i class="fas fa-info-circle"></i>
              <div>
                <strong>Supported columns:</strong> Email, FirstName, LastName, Company, Country, Phone, CustomField1-5<br>
                Duplicates will be skipped automatically.
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="showImportModal = false">Cancel</button>
            <button class="btn btn-primary" (click)="doImport()" [disabled]="!importFile || importing">
              <span *ngIf="importing" class="spinner"></span>
              {{ importing ? 'Importing...' : 'Import Contacts' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Add/Edit Modal -->
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
              <span *ngIf="saving" class="spinner"></span>
              Save Contact
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ContactsComponent implements OnInit {
  contacts: Contact[] = [];
  page: PageResponse<Contact> = { content: [], totalElements: 0, totalPages: 0, size: 50, number: 0, first: true, last: true };
  loading = false;
  currentPage = 0;
  searchQuery = '';
  selectedStatus = '';
  selectedIds = new Set<number>();
  contactStats: Record<string, number> = {};
  Math = Math;

  showImportModal = false;
  showEditModal = false;
  importFile: File | null = null;
  importTagsInput = '';
  importing = false;
  isDragging = false;
  editingContact: Partial<Contact> | null = null;
  saving = false;

  statusPills = [
    { key: 'total', label: 'Total', class: 'all' },
    { key: 'active', label: 'Active', class: 'active' },
    { key: 'unsubscribed', label: 'Unsubscribed', class: 'unsubscribed' },
    { key: 'bounced', label: 'Bounced', class: 'bounced' }
  ];

  private searchTimer: any;

  constructor(private api: ApiService, private toast: ToastService) {}

  ngOnInit(): void {
    this.loadContacts();
    this.loadStats();
  }

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

  doImport(): void {
    if (!this.importFile) return;
    this.importing = true;
    const tags = this.importTagsInput ? this.importTagsInput.split(',').map(t => t.trim()).filter(Boolean) : [];
    this.api.importContacts(this.importFile, tags).subscribe({
      next: (result) => {
        this.toast.success('Import Complete',
          `Imported: ${result.imported}, Duplicates: ${result.duplicates}, Invalid: ${result.invalid}`);
        this.showImportModal = false;
        this.importing = false;
        this.importFile = null;
        this.loadContacts();
        this.loadStats();
      },
      error: () => { this.importing = false; this.toast.error('Import failed'); }
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
}
