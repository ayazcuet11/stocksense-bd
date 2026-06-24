import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { LoginRequest, LoginResponse } from '../models/models';

const TOKEN_KEY = 'ss_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly isLoggedIn = computed(() => !!this._token());
  readonly currentRole = computed(() => this._decodePayload()?.role ?? null);
  readonly currentEmail = computed(() => this._decodePayload()?.sub ?? null);
  readonly currentTenantId = computed(() => this._decodePayload()?.tenantId ?? null);

  constructor(private http: HttpClient, private router: Router) {}

  login(req: LoginRequest) {
    return this.http.post<LoginResponse>('/api/auth/login', req).pipe(
      tap(res => {
        localStorage.setItem(TOKEN_KEY, res.token);
        this._token.set(res.token);
      })
    );
  }

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    this._token.set(null);
    this.router.navigate(['/login']);
  }

  getToken() { return this._token(); }

  private _decodePayload(): any {
    const token = this._token();
    if (!token) return null;
    try {
      return JSON.parse(atob(token.split('.')[1]));
    } catch { return null; }
  }
}
