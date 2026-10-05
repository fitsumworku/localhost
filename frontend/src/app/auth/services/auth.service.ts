import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoginRequest, SignupRequest, AuthResponse } from '../models/auth.model';

// Authentication service for handling user login and signup
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api'; // Base API URL

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, request);
  }

  signup(request: SignupRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/register`, request);
  }

  // TODO: Add JWT token management methods here when implementing JWT logic
  // - saveToken(token: string): void
  // - getToken(): string | null
  // - removeToken(): void
  // - isAuthenticated(): boolean
}
