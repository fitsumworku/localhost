import { Injectable } from '@angular/core';

// One dot on the portfolio graph
export interface ValuePoint {
  time: number; // epoch ms
  value: number; // USD
}

// Remembers the portfolio value each time it changes, per account, so the graph can
// draw a line through those values. Kept in memory: it resets when the page reloads.
@Injectable({ providedIn: 'root' })
export class PortfolioTrackerService {
  private readonly history = new Map<number, ValuePoint[]>();

  // Adds a dot if the value is different from the last one, and returns the points to draw
  record(accountId: number, value: number): ValuePoint[] {
    const points = this.history.get(accountId) ?? [];
    const last = points[points.length - 1];
    if (!last || Math.abs(last.value - value) >= 0.01) {
      points.push({ time: Date.now(), value });
      this.history.set(accountId, points);
    }
    // The line continues to "now" at the latest value
    return [...points, { time: Date.now(), value }];
  }
}
