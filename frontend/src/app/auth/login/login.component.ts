import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../services/auth.service';
import { LoginRequest } from '../models/auth.model';

// A decorative symbol that drifts up the background
interface FloatingSymbol {
  text: string;
  left: number;     // % from the left edge
  size: number;     // font size in px
  duration: number; // seconds for one full float
  delay: number;    // seconds (negative = start mid-animation)
  opacity: number;
}

const SYMBOLS = [
  '$', '+2.4%', '{ }', 'AAPL ▲', '$', '0x1F', '▲ 1.8%', '</>', '€', 'BUY',
  '$', '+12.7%', 'Σ', 'NVDA ▲', '1010', '£', '=>', '$', 'MSFT', '+0.9%', '¥', '[ ]'
];

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule]
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  hidePassword = true;
  isLoading = false;
  errorMessage = '';

  readonly floatingSymbols: FloatingSymbol[] = SYMBOLS.map((text, i) => ({
    text,
    // Spread symbols evenly but irregularly across the screen
    left: (i * 47 + 5) % 96,
    size: 14 + ((i * 7) % 4) * 6,
    duration: 18 + ((i * 11) % 14),
    delay: -((i * 13) % 30),
    opacity: 0.12 + ((i * 5) % 4) * 0.06
  }));

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initializeForm();
  }

  initializeForm(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
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

    this.isLoading = true;
    this.errorMessage = '';

    const loginRequest: LoginRequest = this.loginForm.value;

    this.authService.login(loginRequest).subscribe({
      next: (response: any) => {
        this.isLoading = false;
        // TODO: Save JWT token to sessionStorage when implementing JWT logic
        // this.authService.saveToken(response.token);
        this.router.navigate(['/account-type']); // Navigate to account type selection
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'Login failed. Please try again.';
      }
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
