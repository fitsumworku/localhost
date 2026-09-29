import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { SignupComponent } from './auth/signup/signup.component';
import { AccountTypeComponent } from './auth/account-type/account-type.component';
import { SidebarLayoutComponent } from './dashboard/sidebar-layout/sidebar-layout.component';
import { ClientDashboardComponent } from './dashboard/client-dashboard/client-dashboard.component';
import { AdminDashboardComponent } from './dashboard/admin-dashboard/admin-dashboard.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },
  { path: 'account-type', component: AccountTypeComponent },
  {
    path: '',
    component: SidebarLayoutComponent,
    children: [
      { path: 'client-dashboard', component: ClientDashboardComponent },
      { path: 'admin-dashboard', component: AdminDashboardComponent }
    ]
  }
];
