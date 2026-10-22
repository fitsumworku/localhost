import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
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
import { AuthService } from '../../auth/services/auth.service';

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
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
  ],
})
export class SidebarLayoutComponent {
  // Account selector
  accounts: Account[] = [];
  selectedAccount: Account | null = null;

  clientMenuItems = [
    { label: 'Portfolio', icon: 'work_bag', route: '/client-dashboard' },
    { label: 'Markets', icon: 'trending_up', route: '/markets' },
    { label: 'Compare Stocks', icon: 'join_inner', route: '/compare' },
    { label: 'Transaction History', icon: 'history', route: '/transactions' },
  ];

  adminMenuItems = [
    { label: 'Client Monitor', icon: 'people', route: '/admin-dashboard' },
    { label: 'Disputes', icon: 'gavel', route: '/disputes' },
    { label: 'Compliance', icon: 'verified_user', route: '/compliance' },
  ];

  // From the logged-in user's role, so admins keep the admin menu on every admin page
  readonly isAdmin: boolean;

  constructor(
    private router: Router,
    private dialog: MatDialog,
    private accountService: AccountService,
    private authService: AuthService,
  ) {
    this.isAdmin = this.authService.getCurrentUser()?.role === 'ADMIN';
    if (!this.isAdmin) {
      this.loadAccounts();
    }
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }

  get menuItems() {
    return this.isAdmin ? this.adminMenuItems : this.clientMenuItems;
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
      panelClass: ['dark-ui', 'app-dialog'],
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
      panelClass: ['add-account-dialog-panel', 'dark-ui', 'app-dialog'],
    });

    dialogRef.afterClosed().subscribe((result: { accountName: string } | undefined) => {
      if (result) {
        this.accountService.createAccount(result.accountName).subscribe({
          next: (account: Account) => {
            this.accounts = [...this.accounts, account];
            this.selectAccount(account);
          },
        });
      }
    });
  }
}
