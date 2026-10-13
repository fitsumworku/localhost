import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { SignupComponent } from './auth/signup/signup.component';
import { AuthLayoutComponent } from './auth/auth-layout/auth-layout.component';
import { AccountTypeComponent } from './auth/account-type/account-type.component';
import { SidebarLayoutComponent } from './dashboard/sidebar-layout/sidebar-layout.component';
import { ClientDashboardComponent } from './dashboard/client-dashboard/client-dashboard.component';
import { AdminDashboardComponent } from './dashboard/admin-dashboard/admin-dashboard.component';
import { MarketsComponent } from './dashboard/markets/markets.component';
import { CompareMarketsComponent } from './dashboard/compare-markets/compare-markets.component';
import { TransactionHistoryComponent } from './dashboard/transaction-history/transaction-history.component';
import { ClientDisputesComponent } from './dashboard/client-disputes/client-disputes.component';
import { ComplianceComponent } from './dashboard/compliance/compliance.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  {
    path: '',
    component: AuthLayoutComponent,
    children: [
      { path: 'login', component: LoginComponent },
      { path: 'signup', component: SignupComponent }
    ]
  },
  { path: 'account-type', component: AccountTypeComponent },
  {
    path: '',
    component: SidebarLayoutComponent,
    children: [
      { path: 'client-dashboard', component: ClientDashboardComponent },
      { path: 'admin-dashboard', component: AdminDashboardComponent },
      { path: 'markets', component: MarketsComponent },
      { path: 'compare', component: CompareMarketsComponent },
      { path: 'transactions', component: TransactionHistoryComponent },
      { path: 'disputes', component: ClientDisputesComponent },
      { path: 'compliance', component: ComplianceComponent }
    ]
  }
];
