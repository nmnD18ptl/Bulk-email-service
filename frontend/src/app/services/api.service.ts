import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Contact, Tag, SmtpConfig, Template, Campaign, CampaignStats,
  SpamAnalysis, WarmupPlan, WarmupScheduleDay, DashboardStats,
  PageResponse, AppSetting, ImportResult
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class ApiService {

  private api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // ---- Contacts ----
  getContacts(params?: any): Observable<PageResponse<Contact>> {
    return this.http.get<PageResponse<Contact>>(`${this.api}/contacts`, { params });
  }

  getContact(id: number): Observable<Contact> {
    return this.http.get<Contact>(`${this.api}/contacts/${id}`);
  }

  createContact(contact: Partial<Contact>): Observable<Contact> {
    return this.http.post<Contact>(`${this.api}/contacts`, contact);
  }

  updateContact(id: number, contact: Partial<Contact>): Observable<Contact> {
    return this.http.put<Contact>(`${this.api}/contacts/${id}`, contact);
  }

  deleteContact(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/contacts/${id}`);
  }

  importContacts(file: File, tags?: string[]): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    if (tags?.length) {
      tags.forEach(t => formData.append('tags', t));
    }
    return this.http.post<ImportResult>(`${this.api}/contacts/import`, formData);
  }

  exportContacts(status?: string): Observable<Blob> {
    const url = status
      ? `${this.api}/contacts/export?status=${encodeURIComponent(status)}`
      : `${this.api}/contacts/export`;
    return this.http.get(url, { responseType: 'blob' });
  }

  getContactStats(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.api}/contacts/stats`);
  }

  validateEmail(email: string): Observable<any> {
    return this.http.post(`${this.api}/contacts/validate-email`, { email });
  }

  // ---- Tags ----
  getTags(): Observable<Tag[]> {
    return this.http.get<Tag[]>(`${this.api}/tags`);
  }

  createTag(tag: Partial<Tag>): Observable<Tag> {
    return this.http.post<Tag>(`${this.api}/tags`, tag);
  }

  updateTag(id: number, tag: Partial<Tag>): Observable<Tag> {
    return this.http.put<Tag>(`${this.api}/tags/${id}`, tag);
  }

  deleteTag(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/tags/${id}`);
  }

  // ---- Campaigns ----
  getCampaigns(page = 0, size = 20): Observable<PageResponse<Campaign>> {
    return this.http.get<PageResponse<Campaign>>(`${this.api}/campaigns`, { params: { page, size } });
  }

  getCampaign(id: number): Observable<Campaign> {
    return this.http.get<Campaign>(`${this.api}/campaigns/${id}`);
  }

  createCampaign(campaign: Partial<Campaign>): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.api}/campaigns`, campaign);
  }

  updateCampaign(id: number, campaign: Partial<Campaign>): Observable<Campaign> {
    return this.http.put<Campaign>(`${this.api}/campaigns/${id}`, campaign);
  }

  deleteCampaign(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/campaigns/${id}`);
  }

  sendCampaign(id: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.api}/campaigns/${id}/send`, {});
  }

  pauseCampaign(id: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.api}/campaigns/${id}/pause`, {});
  }

  resumeCampaign(id: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.api}/campaigns/${id}/resume`, {});
  }

  cancelCampaign(id: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.api}/campaigns/${id}/cancel`, {});
  }

  getCampaignStats(id: number): Observable<CampaignStats> {
    return this.http.get<CampaignStats>(`${this.api}/campaigns/${id}/stats`);
  }

  sendTestEmail(campaignId: number, email: string): Observable<any> {
    return this.http.post(`${this.api}/campaigns/${campaignId}/test`, { email });
  }

  analyzeSpam(subject: string, html: string, sender?: string): Observable<SpamAnalysis> {
    return this.http.post<SpamAnalysis>(`${this.api}/campaigns/analyze-spam`, { subject, html, sender });
  }

  // ---- Templates ----
  getTemplates(category?: string): Observable<Template[]> {
    const url = category
      ? `${this.api}/templates?category=${encodeURIComponent(category)}`
      : `${this.api}/templates`;
    return this.http.get<Template[]>(url);
  }

  getTemplate(id: number): Observable<Template> {
    return this.http.get<Template>(`${this.api}/templates/${id}`);
  }

  createTemplate(template: Partial<Template>): Observable<Template> {
    return this.http.post<Template>(`${this.api}/templates`, template);
  }

  updateTemplate(id: number, template: Partial<Template>): Observable<Template> {
    return this.http.put<Template>(`${this.api}/templates/${id}`, template);
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/templates/${id}`);
  }

  // ---- SMTP Configs ----
  getSmtpConfigs(): Observable<SmtpConfig[]> {
    return this.http.get<SmtpConfig[]>(`${this.api}/smtp-configs`);
  }

  createSmtpConfig(config: any): Observable<SmtpConfig> {
    return this.http.post<SmtpConfig>(`${this.api}/smtp-configs`, config);
  }

  updateSmtpConfig(id: number, config: any): Observable<SmtpConfig> {
    return this.http.put<SmtpConfig>(`${this.api}/smtp-configs/${id}`, config);
  }

  deleteSmtpConfig(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/smtp-configs/${id}`);
  }

  testSmtpConnection(id: number): Observable<any> {
    return this.http.post(`${this.api}/smtp-configs/${id}/test`, {});
  }

  testSmtpParams(params: any): Observable<any> {
    return this.http.post(`${this.api}/smtp-configs/test-params`, params);
  }

  // ---- Analytics ----
  getDashboard(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.api}/analytics/dashboard`);
  }

  getCampaignAnalytics(id: number): Observable<any> {
    return this.http.get(`${this.api}/analytics/campaign/${id}`);
  }

  exportCampaignReport(id: number): Observable<Blob> {
    return this.http.get(`${this.api}/analytics/export/${id}`, { responseType: 'blob' });
  }

  exportPerRecipientReport(id: number): Observable<Blob> {
    return this.http.get(`${this.api}/analytics/export/${id}/recipients`, { responseType: 'blob' });
  }

  checkDomainHealth(domain: string): Observable<any> {
    return this.http.get(`${this.api}/domain/health?domain=${encodeURIComponent(domain)}`);
  }

  // ---- Settings ----
  getSettings(category?: string): Observable<AppSetting[]> {
    const url = category
      ? `${this.api}/settings?category=${encodeURIComponent(category)}`
      : `${this.api}/settings`;
    return this.http.get<AppSetting[]>(url);
  }

  updateSetting(key: string, value: string): Observable<AppSetting> {
    return this.http.put<AppSetting>(`${this.api}/settings/${key}`, { value });
  }

  bulkUpdateSettings(settings: Record<string, string>): Observable<AppSetting[]> {
    return this.http.post<AppSetting[]>(`${this.api}/settings/bulk`, settings);
  }

  // ---- Warmup ----
  getWarmupPlans(): Observable<WarmupPlan[]> {
    return this.http.get<WarmupPlan[]>(`${this.api}/warmup`);
  }

  createWarmupPlan(plan: Partial<WarmupPlan>): Observable<WarmupPlan> {
    return this.http.post<WarmupPlan>(`${this.api}/warmup`, plan);
  }

  startWarmup(id: number): Observable<WarmupPlan> {
    return this.http.post<WarmupPlan>(`${this.api}/warmup/${id}/start`, {});
  }

  pauseWarmup(id: number): Observable<WarmupPlan> {
    return this.http.post<WarmupPlan>(`${this.api}/warmup/${id}/pause`, {});
  }

  resumeWarmup(id: number): Observable<WarmupPlan> {
    return this.http.post<WarmupPlan>(`${this.api}/warmup/${id}/resume`, {});
  }

  deleteWarmupPlan(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/warmup/${id}`);
  }

  getWarmupSchedule(targetVolume: number): Observable<WarmupScheduleDay[]> {
    return this.http.get<WarmupScheduleDay[]>(`${this.api}/warmup/schedule/${targetVolume}`);
  }
}
