import { AfterViewInit, Component, DestroyRef, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { Event as RouterEvent, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';

type AuthVariant = 'login' | 'signup';

// A decorative symbol that drifts up the background
interface FloatingSymbol {
  text: string;
  left: number; // % from the left edge
  size: number; // font size in px
  duration: number; // seconds for one full float
  delay: number; // seconds (negative = start mid-animation)
  opacity: number;
}

const SYMBOLS: Record<AuthVariant, string[]> = {
  login: [
    '$', '+2.4%', '{ }', 'AAPL ▲', '$', '( )', '▲ 1.8%', '</>', '€', 'BUY', '$',
    '+12.7%', 'Σ', 'NVDA ▲', '1010', '£', '=>', '$', 'MSFT', '+0.9%', '¥', '[ ]',
  ],
  signup: [
    '₿', 'TSLA ▲', '+5.3%', '&&', 'AMZN', '%', '▲ 3.1%', '::', '₹', 'HOLD', 'Δ',
    'GOOGL ▲', '+8.6%', '++', 'ETF', '₩', '!=', 'META ▲', '0x1F', '+1.4%', '∞', '//',
  ],
};

// Chart points share x positions so one shape can morph smoothly into the other
const CHART_X = [
  0, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600,
  660, 720, 780, 840, 900, 960, 1020, 1080, 1140, 1170, 1200,
];
const CHART_Y: Record<AuthVariant, number[]> = {
  // Steady climb
  login: [
    520, 500, 510, 478, 488, 452, 466, 430, 442, 404, 388,
    398, 356, 366, 322, 300, 314, 262, 240, 196, 150, 118,
  ],
  // Dip, recovery, then a sharp breakout
  signup: [
    430, 452, 440, 470, 494, 478, 506, 492, 462, 478, 444,
    420, 434, 396, 410, 372, 340, 352, 290, 246, 178, 132,
  ],
};
const DOT_INDEX = 20;
const MORPH_MS = 1400;

@Component({
  selector: 'app-auth-layout',
  templateUrl: './auth-layout.component.html',
  styleUrl: './auth-layout.component.scss',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
})
export class AuthLayoutComponent implements AfterViewInit {
  @ViewChild('chartLine') chartLine!: ElementRef<SVGPathElement>;
  @ViewChild('chartArea') chartArea!: ElementRef<SVGPathElement>;
  @ViewChild('chartDot') chartDot!: ElementRef<SVGCircleElement>;

  readonly variant = signal<AuthVariant>('login');

  readonly symbolLayers = (Object.keys(SYMBOLS) as AuthVariant[]).map((variant, v) => ({
    variant,
    symbols: SYMBOLS[variant].map(
      (text, i): FloatingSymbol => ({
        text,
        // Spread symbols evenly but irregularly; offset each layer so they don't line up
        left: (i * 47 + 5 + v * 23) % 96,
        size: 14 + ((i * 7) % 4) * 6,
        duration: 18 + ((i * 11) % 14),
        delay: -((i * 13 + v * 7) % 30),
        opacity: 0.12 + ((i * 5) % 4) * 0.06,
      }),
    ),
  }));

  private router = inject(Router);
  private currentY = [...CHART_Y.login];
  private frame = 0;
  private viewReady = false;

  constructor() {
    const sub = this.router.events.subscribe((e: RouterEvent) => {
      if (e instanceof NavigationEnd) {
        this.setVariant(e.urlAfterRedirects.startsWith('/signup') ? 'signup' : 'login');
      }
    });

    inject(DestroyRef).onDestroy(() => {
      sub.unsubscribe();
      cancelAnimationFrame(this.frame);
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    // First paint: no morph, just draw the current page's chart
    this.currentY = [...CHART_Y[this.variant()]];
    this.drawChart(this.currentY);
  }

  private setVariant(variant: AuthVariant): void {
    if (variant === this.variant()) return;
    this.variant.set(variant);
    if (this.viewReady) this.morphTo(CHART_Y[variant]);
  }

  // Animate the chart from wherever it is now to the target shape
  private morphTo(targetY: number[]): void {
    cancelAnimationFrame(this.frame);

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      this.currentY = [...targetY];
      this.drawChart(this.currentY);
      return;
    }

    const fromY = [...this.currentY];
    const start = performance.now();

    const step = (now: number) => {
      const t = Math.min((now - start) / MORPH_MS, 1);
      const eased = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; // ease-in-out cubic
      this.currentY = fromY.map((y, i) => y + (targetY[i] - y) * eased);
      this.drawChart(this.currentY);
      if (t < 1) this.frame = requestAnimationFrame(step);
    };
    this.frame = requestAnimationFrame(step);
  }

  // Written straight to the DOM so each animation frame doesn't need change detection
  private drawChart(ys: number[]): void {
    const line = 'M' + CHART_X.map((x, i) => `${x} ${ys[i].toFixed(1)}`).join(' L');
    this.chartLine.nativeElement.setAttribute('d', line);
    this.chartArea.nativeElement.setAttribute('d', `${line} L1200 600 L0 600 Z`);
    this.chartDot.nativeElement.setAttribute('cx', String(CHART_X[DOT_INDEX]));
    this.chartDot.nativeElement.setAttribute('cy', ys[DOT_INDEX].toFixed(1));
  }
}
