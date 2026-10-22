import { Injectable } from '@angular/core';
import { AuthService } from '../../auth/services/auth.service';
import { Security } from '../markets/services/market-data.service';
import { ValueRange } from '../services/account.service';

export interface CompareSelection {
  securities: [Security | null, Security | null];
  range: ValueRange;
}

const RANGES: ValueRange[] = ['1D', '1W', '1M', '1Y'];

// Remembers the two markets being compared, so leaving the page (or reloading) doesn't lose them.
// Kept in sessionStorage like the login session, per user, so it clears when the tab closes.
@Injectable({ providedIn: 'root' })
export class CompareSelectionService {
  constructor(private authService: AuthService) {}

  load(): CompareSelection {
    const empty: CompareSelection = { securities: [null, null], range: '1M' };
    try {
      const raw = sessionStorage.getItem(this.key());
      if (!raw) return empty;
      const saved = JSON.parse(raw) as Partial<CompareSelection>;
      const securities = Array.isArray(saved.securities) ? saved.securities : [];
      return {
        securities: [this.validSecurity(securities[0]), this.validSecurity(securities[1])],
        range: RANGES.includes(saved.range as ValueRange) ? (saved.range as ValueRange) : '1M',
      };
    } catch {
      return empty; // storage blocked or unreadable: start fresh
    }
  }

  save(selection: CompareSelection): void {
    try {
      sessionStorage.setItem(this.key(), JSON.stringify(selection));
    } catch {
      // Storage blocked; the comparison just won't survive leaving the page
    }
  }

  private key(): string {
    return `compare_selection_${this.authService.getCurrentUser()?.userId ?? 'anonymous'}`;
  }

  private validSecurity(value: unknown): Security | null {
    const security = value as Security | null;
    return security && typeof security.securityId === 'number' && typeof security.ticker === 'string' ? security : null;
  }
}
