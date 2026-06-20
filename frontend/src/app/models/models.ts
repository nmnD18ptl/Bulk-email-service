export interface Contact {
  id?: number;
  email: string;
  firstName?: string;
  lastName?: string;
  company?: string;
  country?: string;
  phone?: string;
  customField1?: string;
  customField2?: string;
  customField3?: string;
  customField4?: string;
  customField5?: string;
  status: 'ACTIVE' | 'UNSUBSCRIBED' | 'BOUNCED' | 'COMPLAINED' | 'INVALID';
  emailVerified?: boolean;
  engagementScore?: number;
  lastOpenedAt?: string;
  subscribedAt?: string;
  createdAt?: string;
  tags?: Tag[];
}

export interface Tag {
  id?: number;
  name: string;
  color?: string;
  description?: string;
  contactCount?: number;
}

export interface SmtpConfig {
  id?: number;
  name: string;
  host: string;
  port: number;
  username: string;
  password?: string;
  securityType: 'NONE' | 'TLS' | 'SSL';
  providerType: 'CUSTOM' | 'GMAIL' | 'AMAZON_SES' | 'BREVO' | 'MAILGUN' | 'SENDGRID';
  fromName?: string;
  fromEmail?: string;
  replyToEmail?: string;
  dailyLimit?: number;
  hourlyLimit?: number;
  sentToday?: number;
  isDefault?: boolean;
  isActive?: boolean;
  connectionTested?: boolean;
}

export interface Template {
  id?: number;
  name: string;
  category?: string;
  description?: string;
  subject?: string;
  htmlContent: string;
  textContent?: string;
  isBuiltIn?: boolean;
  createdAt?: string;
}

export interface Campaign {
  id?: number;
  name: string;
  subject?: string;
  previewText?: string;
  htmlContent?: string;
  textContent?: string;
  status: 'DRAFT' | 'SCHEDULED' | 'SENDING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';
  smtpConfig?: SmtpConfig;
  template?: Template;
  sendToAll?: boolean;
  tagFilters?: number[];
  fromName?: string;
  fromEmail?: string;
  replyToEmail?: string;
  scheduledAt?: string;
  startedAt?: string;
  completedAt?: string;
  totalRecipients?: number;
  sentCount?: number;
  openCount?: number;
  clickCount?: number;
  bounceCount?: number;
  unsubscribeCount?: number;
  failedCount?: number;
  openRate?: number;
  clickRate?: number;
  bounceRate?: number;
  batchSize?: number;
  batchDelaySeconds?: number;
  interEmailDelayMs?: number;
  maxRetries?: number;
  physicalAddress?: string;
  trackOpens?: boolean;
  trackClicks?: boolean;
  createdAt?: string;
}

export interface CampaignStats {
  totalRecipients: number;
  sentCount: number;
  deliveredCount: number;
  openCount: number;
  clickCount: number;
  bounceCount: number;
  unsubscribeCount: number;
  complaintCount: number;
  failedCount: number;
  pendingCount: number;
  openRate: number;
  clickRate: number;
  bounceRate: number;
  status: string;
}

export interface SpamIssue {
  code: string;
  message: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  recommendation: string;
}

export interface SpamAnalysis {
  score: number;
  rating: 'GOOD' | 'WARNING' | 'POOR';
  issues: SpamIssue[];
}

export interface WarmupPlan {
  id?: number;
  name: string;
  smtpConfig?: SmtpConfig;
  status: 'NOT_STARTED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'FAILED';
  targetDailyVolume?: number;
  currentStage?: number;
  totalStages?: number;
  currentDayVolume?: number;
  startDate?: string;
  estimatedCompletionDate?: string;
  bounceRateThreshold?: number;
  complaintRateThreshold?: number;
}

export interface WarmupScheduleDay {
  day: number;
  volume: number;
  date: string;
}

export interface DashboardStats {
  totalContacts: number;
  activeContacts: number;
  totalCampaigns: number;
  totalSent: number;
  totalOpens: number;
  totalClicks: number;
  totalBounces: number;
  avgOpenRate: number;
  avgClickRate: number;
  avgBounceRate: number;
  recentCampaigns: Campaign[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

export interface AppSetting {
  id?: number;
  settingKey: string;
  settingValue?: string;
  description?: string;
  category?: string;
}

export interface ImportResult {
  imported: number;
  duplicates: number;
  invalid: number;
  total: number;
}
