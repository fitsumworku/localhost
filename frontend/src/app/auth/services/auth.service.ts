import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoginRequest, SignupRequest, AuthResponse, User } from '../models/auth.model';

// Authentication service for handling user login and signup
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  // Relative so it goes to the same origin as the page; proxy.conf.json (dev) forwards /api to Spring on :8081
  private apiUrl = '/api';

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, request);
  }

  // Returns the created user (201)
  signup(request: SignupRequest): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/register`, request);
  }

  saveToken(token: string): void {
    sessionStorage.setItem('access_token', token);
  }

  getToken(): string | null {
    return sessionStorage.getItem('access_token');
  }

  removeToken(): void {
    sessionStorage.removeItem('access_token');
  }
}
