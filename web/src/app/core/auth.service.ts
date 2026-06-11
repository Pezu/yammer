import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

/** The super-admin role name (matches the backend UserPrincipal.SUPER). */
export const ROLE_SUPER = 'SUPER';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  roles: string[];
  clientId: string | null;
}

interface StoredSession {
  token: string;
  username: string;
  roles: string[];
  clientId: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly sessionKey = 'yammer.session';

  /** Reactive view of the current session (null when logged out). */
  readonly session = signal<StoredSession | null>(this.restore());

  /** Whether the logged-in user has the SUPER role. */
  readonly isSuper = computed(() => (this.session()?.roles ?? []).includes(ROLE_SUPER));

  /** The client the user belongs to (null for SUPER / logged out). */
  readonly clientId = computed(() => this.session()?.clientId ?? null);

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, req)
      .pipe(
        tap((res) =>
          this.setSession({
            token: res.token,
            username: res.username,
            roles: res.roles ?? [],
            clientId: res.clientId ?? null,
          }),
        ),
      );
  }

  logout(): void {
    this.setSession(null);
  }

  isAuthenticated(): boolean {
    return this.session() !== null;
  }

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  get roles(): string[] {
    return this.session()?.roles ?? [];
  }

  private setSession(session: StoredSession | null): void {
    this.session.set(session);
    if (session) {
      localStorage.setItem(this.sessionKey, JSON.stringify(session));
    } else {
      localStorage.removeItem(this.sessionKey);
    }
  }

  private restore(): StoredSession | null {
    const raw = localStorage.getItem(this.sessionKey);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as StoredSession;
    } catch {
      localStorage.removeItem(this.sessionKey);
      return null;
    }
  }
}
