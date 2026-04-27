import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';

export interface AuthUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  role: 'OWNER' | 'ADMIN' | 'OPERATOR' | 'VIEWER';
  fullName: string;
}

export interface AuthOrg {
  id: number;
  name: string;
  slug: string;
  plan: string;
  monthlyEmailLimit: number;
  emailsSentThisMonth: number;
  maxContacts: number;
}

export interface AuthState {
  user: AuthUser;
  organization: AuthOrg;
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'bep_jwt';
  private readonly STATE_KEY = 'bep_auth';
  private api = environment.apiUrl.replace('/api', '');

  currentState$ = new BehaviorSubject<AuthState | null>(this.loadState());

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string): Observable<AuthState> {
    return this.http.post<AuthState>(`${this.api}/api/auth/login`, { email, password })
      .pipe(tap(state => this.persist(state)));
  }

  register(orgName: string, email: string, password: string,
           firstName: string, lastName: string): Observable<AuthState> {
    return this.http.post<AuthState>(`${this.api}/api/auth/register`,
      { organizationName: orgName, email, password, firstName, lastName })
      .pipe(tap(state => this.persist(state)));
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.STATE_KEY);
    this.currentState$.next(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  get currentUser(): AuthUser | null {
    return this.currentState$.value?.user ?? null;
  }

  get currentOrg(): AuthOrg | null {
    return this.currentState$.value?.organization ?? null;
  }

  get isLoggedIn(): boolean {
    return !!this.getToken();
  }

  canDo(requiredRoles: string[]): boolean {
    const role = this.currentUser?.role;
    return role ? requiredRoles.includes(role) : false;
  }

  private persist(state: AuthState): void {
    localStorage.setItem(this.TOKEN_KEY, state.token);
    localStorage.setItem(this.STATE_KEY, JSON.stringify(state));
    this.currentState$.next(state);
  }

  private loadState(): AuthState | null {
    try {
      const raw = localStorage.getItem(this.STATE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }
}
