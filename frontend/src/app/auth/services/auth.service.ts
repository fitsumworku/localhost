import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { LoginRequest, SignupRequest, AuthResponse, User } from '../models/auth.model';

interface StoredSession {
  token: string;
  expiresAt: number;
  user: User;
}
const SESSION_KEY = 'auth_session';

// Authentication service for handling user login and signup
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  // Relative so it goes to the same origin as the page; proxy.conf.json (dev) forwards /api to Spring on :8081
  private apiUrl = '/api';

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/auth/login`, request)
      .pipe(tap((response) => this.saveSession(response)));
  }

  // Returns the created user (201)
  signup(request: SignupRequest): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/register`, request);
  }

  // The JWT to send as "Authorization: Bearer <token>", or null if missing/expired
  getToken(): string | null {
    const session = this.readSession();
    if (!session) return null;

    if (Date.now() >= session.expiresAt) {
      this.clearSession(); // don't keep a dead token around
      return null;
    }
    return session.token;
  }

  // The logged-in user (name, role, ...), or null if not logged in
  getCurrentUser(): User | null {
    return this.getToken() ? this.readSession()!.user : null;
  }

  // Log out locally: forget the token and user
  clearSession(): void {
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      // Storage blocked (e.g. some private windows); nothing to clear
    }
  }

  getRole(): string | null {
    const user = this.getCurrentUser();
    return user ? user.role : null;
  }

  // Store the login response; expiresInMs is turned into a fixed moment so it survives refreshes
  private saveSession(response: AuthResponse): void {
    const session: StoredSession = {
      token: response.accessToken,
      expiresAt: Date.now() + response.expiresInMs,
      user: response.user,
    };
    try {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } catch {
      // Storage blocked; the user will need to log in again after a refresh
    }
  }

  // sessionStorage only holds text, so parse it back into an object
  private readSession(): StoredSession | null {
    try {
      const raw = sessionStorage.getItem(SESSION_KEY);
      return raw ? (JSON.parse(raw) as StoredSession) : null;
    } catch {
      return null;
    }
  }
}
