import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export type AccountType = 'client' | 'admin';

@Component({
  selector: 'app-account-type',
  templateUrl: './account-type.component.html',
  styleUrl: './account-type.component.scss',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule
  ]
})
export class AccountTypeComponent {
  isLoading = false;
  selectedType: AccountType | null = null;

  constructor(private router: Router) {}

  selectAccountType(accountType: AccountType): void {
    this.isLoading = true;
    this.selectedType = accountType;

    // Simulate loading delay for better UX
    setTimeout(() => {
      if (accountType === 'client') {
        this.router.navigate(['/client-dashboard']);
      } else if (accountType === 'admin') {
        this.router.navigate(['/admin-dashboard']);
      }
    }, 500);
  }

  isSelected(accountType: AccountType): boolean {
    return this.selectedType === accountType;
  }
}
