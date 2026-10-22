import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';

type ClientStatus = 'active' | 'blacklisted';

interface Client {
  id: string;
  name: string;
  status: ClientStatus;
}

@Component({
  selector: 'app-compliance',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
  ],
  templateUrl: './compliance.component.html',
  styleUrl: './compliance.component.scss',
})
export class ComplianceComponent implements OnInit {
  searchQuery = '';

  // Client data
  allClients: Client[] = [];
  displayedClients: Client[] = [];

  displayedColumns: string[] = ['clientId', 'name', 'status'];

  ngOnInit(): void {
    this.filterClients();
  }

  filterClients(): void {
    let filtered = this.allClients;

    // Apply search filter
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(
        client =>
          client.id.toLowerCase().includes(query) ||
          client.name.toLowerCase().includes(query)
      );
    }

    this.displayedClients = filtered;
  }

  onSearchChange(): void {
    this.filterClients();
  }

  toggleClientStatus(client: Client): void {
    client.status = client.status === 'active' ? 'blacklisted' : 'active';
    console.log(`Client ${client.id} status changed to ${client.status}`);
    // TODO: Call API to update client status
  }

  getStatusTone(status: ClientStatus): string {
    return status === 'active' ? 'positive' : 'negative';
  }

  getStatusLabel(status: ClientStatus): string {
    return status === 'active' ? 'Active' : 'Blacklisted';
  }
}
