import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
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
  readonly signedOutMessage = signal('');

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.initializeForm();
    if (this.route.snapshot.queryParamMap.has('signedOut')) {
      this.signedOutMessage.set('You have been signed out. Please sign in again to continue.');
    }
  }

  initializeForm(): void {
    this.loginForm = this.fb.group({
      email: ['kent@gmail.com', [Validators.required, Validators.email]],
      password: ['testpassword123', [Validators.required, Validators.minLength(12)]],
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
    this.signedOutMessage.set('');

    const loginRequest: LoginRequest = this.loginForm.value;

    this.authService.login(loginRequest).subscribe({
      next: () => {
        this.isLoading.set(false);

        if (this.authService.getRole() === 'ADMIN') {
          this.router.navigate(['/admin-dashboard']);
        } else {
          this.router.navigate(['/client-dashboard']);
        }
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
