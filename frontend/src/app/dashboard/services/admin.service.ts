import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------- Reports (read from the OLAP snapshot) ----------

export interface ReportFreshness {
  sourceSnapshotAt: string;
  lastSuccessfulAt: string;
}

export interface Report<T> {
  freshness: ReportFreshness;
  data: T;
}

export interface VolumeTotals {
  grossVolumeUsd: number;
  buyVolumeUsd: number;
  sellVolumeUsd: number;
  tradeCount: number;
  buyTradeCount: number;
  sellTradeCount: number;
}

export interface UserMetrics {
  totalClients: number;
  activeClients: number;
  blacklistedClients: number;
  totalAdmins: number;
  tradingClientsInPeriod: number;
}

export interface ReportSummary {
  volume: VolumeTotals;
  orders: { submittedTotal: number; currentStatusCounts: Record<string, number> };
  users: UserMetrics;
}

export interface SecurityVolume {
  securityId: number;
  ticker: string;
  name: string;
  exchange: string;
  assetType: string;
  sector: string;
  volume: VolumeTotals;
  quantityTraded: number;
}

export interface UserVolume {
  userId: number;
  name: string;
  volume: VolumeTotals;
}

// ---------- Disputes (live, OLTP) ----------

export type DisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'ESCALATED' | 'RESOLVED' | 'REJECTED';

export interface DisputeResponse {
  responseId: number;
  adminId: number;
  adminName: string;
  message: string;
  createdDate: string;
}

export interface AdminDispute {
  disputeId: number;
  accountId: number;
  clientId: number;
  clientName: string;
  clientEmail: string;
  adminId: number | null;
  adminName: string | null;
  transactionId: number | null;
  tradeId: number | null;
  disputeType: string;
  description: string | null;
  disputedAmount: number | null;
  status: DisputeStatus;
  dateCreated: string;
  dateResolved: string | null;
  responses: DisputeResponse[];
}

export type TradeSide = 'BUY' | 'SELL';

/** Optional report window; from is inclusive and to is exclusive. */
export interface ReportRange {
  from?: string;
  to?: string;
}

// ---------- Users (live, OLTP) ----------

export type UserStatus = 'ACTIVE' | 'SUSPENDED';

export interface AdminUser {
  userId: number;
  name: string;
  email: string;
  role: 'CLIENT' | 'ADMIN';
  createdDate: string;
  updatedDate: string;
  status: UserStatus;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly apiUrl = '/api';

  constructor(private http: HttpClient) {}

  summary(): Observable<Report<ReportSummary>> {
    return this.http.get<Report<ReportSummary>>(`${this.apiUrl}/reports/summary`);
  }

  topSecurities(limit = 10): Observable<Report<SecurityVolume[]>> {
    return this.http.get<Report<SecurityVolume[]>>(`${this.apiUrl}/reports/volume/securities`, {
      params: new HttpParams().set('limit', limit),
    });
  }

  /** side ranks by amount bought or sold; omit it to rank by gross volume. */
  topClients(limit = 10, side: TradeSide | null = null, range: ReportRange = {}): Observable<Report<UserVolume[]>> {
    let params = new HttpParams().set('limit', limit);
    if (side) params = params.set('side', side);
    if (range.from) params = params.set('from', range.from);
    if (range.to) params = params.set('to', range.to);
    return this.http.get<Report<UserVolume[]>>(`${this.apiUrl}/reports/volume/users`, { params });
  }

  disputes(status: DisputeStatus | null, query: string, size = 100): Observable<PageResponse<AdminDispute>> {
    let params = new HttpParams().set('size', size);
    if (status) params = params.set('status', status);
    if (query.trim()) params = params.set('q', query.trim());
    return this.http.get<PageResponse<AdminDispute>>(`${this.apiUrl}/disputes`, { params });
  }

  updateDisputeStatus(dispute: AdminDispute, disputeStatus: DisputeStatus): Observable<unknown> {
    return this.http.patch(
      `${this.apiUrl}/accounts/${dispute.accountId}/disputes/${dispute.disputeId}/status`,
      { disputeStatus },
    );
  }

  respondToDispute(dispute: AdminDispute, message: string): Observable<DisputeResponse> {
    return this.http.post<DisputeResponse>(
      `${this.apiUrl}/accounts/${dispute.accountId}/disputes/${dispute.disputeId}/responses`,
      { message },
    );
  }

  clients(status: UserStatus | null, query: string, size = 100): Observable<PageResponse<AdminUser>> {
    let params = new HttpParams().set('role', 'CLIENT').set('size', size).set('sort', 'name,asc');
    if (status) params = params.set('status', status);
    if (query.trim()) params = params.set('q', query.trim());
    return this.http.get<PageResponse<AdminUser>>(`${this.apiUrl}/users`, { params });
  }

  setUserStatus(userId: number, status: UserStatus): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.apiUrl}/users/${userId}/status`, { status });
  }
}
