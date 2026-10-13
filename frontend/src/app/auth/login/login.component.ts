import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../services/auth.service';
import { LoginRequest } from '../models/auth.model';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule],
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  hidePassword = true;
  // Signals so the template re-renders when HTTP callbacks change them (the app is zoneless)
  readonly isLoading = signal(false);
  readonly errorMessage = signal('');

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.initializeForm();
  }

  initializeForm(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });
  }

  togglePasswordVisibility(): void {
    this.hidePassword = !this.hidePassword;
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    const loginRequest: LoginRequest = this.loginForm.value;

    this.authService.login(loginRequest).subscribe({
      next: (response: any) => {
        this.isLoading.set(false);
        // TODO: Save JWT token to sessionStorage when implementing JWT logic
        // this.authService.saveToken(response.accessToken);
        this.router.navigate(['/account-type']); // Navigate to account type selection
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.errorMessage.set(error.error?.message || 'Login failed. Please try again.');
      },
    });
  }

  navigateToSignup(): void {
    this.router.navigate(['/signup']);
  }

  get email() {
    return this.loginForm.get('email');
  }

  get password() {
    return this.loginForm.get('password');
  }
}
