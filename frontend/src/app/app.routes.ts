import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/auth/register.component').then(m => m.RegisterComponent)
  },
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'contacts',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/contacts/contacts.component').then(m => m.ContactsComponent)
  },
  {
    path: 'campaigns',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/campaigns/campaigns.component').then(m => m.CampaignsComponent)
  },
  {
    path: 'campaigns/new',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/campaigns/campaign-builder.component').then(m => m.CampaignBuilderComponent)
  },
  {
    path: 'campaigns/:id/edit',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/campaigns/campaign-builder.component').then(m => m.CampaignBuilderComponent)
  },
  {
    path: 'campaigns/:id/stats',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/campaigns/campaign-stats.component').then(m => m.CampaignStatsComponent)
  },
  {
    path: 'templates',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/templates/templates.component').then(m => m.TemplatesComponent)
  },
  {
    path: 'analytics',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/analytics/analytics.component').then(m => m.AnalyticsComponent)
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent)
  },
  {
    path: 'warmup',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/warmup/warmup.component').then(m => m.WarmupComponent)
  },
  {
    path: 'users',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/users/users.component').then(m => m.UsersComponent)
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
