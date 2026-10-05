import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { RouterOutlet } from '@angular/router';

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
    RouterOutlet
  ]
})
export class SidebarLayoutComponent {
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

  constructor(private router: Router) {
    // Determine which dashboard we're on
    this.isAdminDashboard = router.url.includes('admin-dashboard');
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
}
