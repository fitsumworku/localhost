import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { RouterOutlet } from '@angular/router';
import { AddAccountDialogComponent } from './add-account-dialog/add-account-dialog.component';
import { Account, AccountPage, AccountService } from '../services/account.service';
import { CashMovementDialogComponent } from './cash-movement-dialog/cash-movement-dialog.component';

@Component({
  selector: 'app-sidebar-layout',
  templateUrl: './sidebar-layout.component.html',
  styleUrl: './sidebar-layout.component.scss',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    MatDialogModule,
    RouterOutlet
  ]
})
export class SidebarLayoutComponent {
  // Account selector
  accounts: Account[] = [];
  selectedAccount: Account | null = null;

  clientMenuItems = [
    { label: 'Portfolio', icon: 'work_bag', route: '/client-dashboard' },
    { label: 'Markets', icon: 'trending_up', route: '/markets' },
    { label: 'Compare Markets', icon: 'join_inner', route: '/compare' },
    { label: 'Transaction History', icon: 'history', route: '/transactions' },
    { label: 'Settings', icon: 'settings', route: '/settings' }
  ];

  adminMenuItems = [
    { label: 'Client Monitor', icon: 'people', route: '/admin-dashboard' },
    { label: 'Disputes', icon: 'gavel', route: '/disputes' },
    { label: 'Compliance', icon: 'verified_user', route: '/compliance' },
    { label: 'Settings', icon: 'settings', route: '/settings' }
  ];

  isAdminDashboard = false;

  constructor(private router: Router, private dialog: MatDialog, private accountService: AccountService) {
    // Determine which dashboard we're on
    this.isAdminDashboard = router.url.includes('admin-dashboard');
    if (!this.isAdminDashboard) {
      this.loadAccounts();
    }
  }

  logout(): void {
    // TODO: Clear sessionStorage and perform logout
    this.router.navigate(['/login']);
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
  }

  get menuItems() {
    return this.isAdminDashboard ? this.adminMenuItems : this.clientMenuItems;
  }

  selectAccount(account: Account): void {
    this.selectedAccount = account;
    this.accountService.selectAccount(account);
  }

  openCashMovement(type: 'deposit' | 'withdrawal'): void {
    if (!this.selectedAccount) return;

    const dialogRef = this.dialog.open(CashMovementDialogComponent, {
      width: '420px',
      data: { accountId: this.selectedAccount.accountId, type },
    });

    dialogRef.afterClosed().subscribe((completed: boolean) => {
      if (completed) this.loadAccounts();
    });
  }

  private loadAccounts(): void {
    this.accountService.loadAccounts().subscribe({
      next: (page: AccountPage) => {
        this.accountService.setAccounts(page.content);
        this.accounts = page.content;
        this.selectedAccount = this.accountService.selectedAccount();
      },
    });
  }

  openAddAccountDialog(): void {
    const dialogRef = this.dialog.open(AddAccountDialogComponent, {
      width: '90vw',
      maxWidth: '400px',
      minWidth: '250px',
      disableClose: false,
      panelClass: 'add-account-dialog-panel'
    });

    dialogRef.afterClosed().subscribe((accountName: string) => {
      if (accountName) {
        this.accountService.createAccount().subscribe({
          next: (account: Account) => {
            this.accounts = [...this.accounts, account];
            this.selectAccount(account);
          },
        });
      }
    });
  }
}
