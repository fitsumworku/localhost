import { Component, DestroyRef, ElementRef, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ValuePoint } from '../portfolio-tracker.service';

interface Tick {
  pos: number;
  label: string;
}

const HEIGHT = 260;
const PAD = { top: 16, right: 20, bottom: 30, left: 64 };

// Exact value for the tooltip: small prices (FX rates, cheap coins) keep up to 4 decimals
function valueFormat(currency: string, value: number): Intl.NumberFormat {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: Math.abs(value) < 10 ? 4 : 2,
  });
}

// Axis labels: compact for large values, otherwise just enough decimals to tell the ticks apart
function axisFormat(currency: string, step: number, max: number): Intl.NumberFormat {
  if (Math.abs(max) >= 10_000) {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency, notation: 'compact', maximumFractionDigits: 1 });
  }
  // Fewest decimals that show the step exactly (50 -> 0, 2.5 -> 1, 0.005 -> 3)
  let decimals = 0;
  while (decimals < 4 && Math.abs(Math.round(step * 10 ** decimals) - step * 10 ** decimals) > 1e-9) decimals++;
  return new Intl.NumberFormat('en-US', { style: 'currency', currency, minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

// Simple line chart of a value over time (portfolio value or a price), drawn as inline SVG
@Component({
  selector: 'app-value-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './value-chart.component.html',
  styleUrl: './value-chart.component.scss',
})
export class ValueChartComponent {
  readonly points = input.required<ValuePoint[]>();
  readonly label = input('Portfolio value');
  readonly currency = input('USD');
  readonly emptyText = input('Waiting to load portfolio value...');

  // Drawn left to right by time, whatever order the caller passed
  readonly sorted = computed(() => [...this.points()].sort((a, b) => a.time - b.time));

  readonly height = HEIGHT;
  readonly pad = PAD;
  readonly width = signal(0);
  readonly activeIndex = signal<number | null>(null);

  // All coordinates are in CSS pixels so the 2px line and the text never get scaled
  readonly geometry = computed(() => {
    const points = this.sorted();
    const width = this.width();
    if (points.length < 2 || width === 0) return null;

    const plotLeft = PAD.left;
    const plotRight = width - PAD.right;
    const plotTop = PAD.top;
    const plotBottom = HEIGHT - PAD.bottom;

    let t0 = points[0].time;
    let t1 = points[points.length - 1].time;
    if (t1 === t0) {
      t0 -= 30_000;
      t1 += 30_000;
    }
    const y = niceScale(Math.min(...points.map((p) => p.value)), Math.max(...points.map((p) => p.value)));

    const xOf = (time: number) => plotLeft + ((time - t0) / (t1 - t0)) * (plotRight - plotLeft);
    const yOf = (value: number) => plotBottom - ((value - y.min) / (y.max - y.min)) * (plotBottom - plotTop);
    const xy = points.map((p) => ({ x: xOf(p.time), y: yOf(p.value) }));

    // Connect the dots with straight lines
    const line = 'M' + xy.map((p) => `${p.x},${p.y}`).join(' L');
    const last = xy[xy.length - 1];
    const area = `${line} L${last.x},${plotBottom} L${xy[0].x},${plotBottom} Z`;

    const axis = axisFormat(this.currency(), y.step, y.max);
    const yTicks: Tick[] = y.ticks.map((value) => ({ pos: yOf(value), label: axis.format(value) }));
    // Drop labels that repeat a neighbour (e.g. everything happened within one minute),
    // always keeping the start and end of the range
    let xTicks: Tick[] = (width < 420 ? [t0, t1] : [t0, (t0 + t1) / 2, t1])
      .map((time) => ({ pos: xOf(time), label: formatTime(time, t1 - t0) }));
    if (xTicks.length === 3 && (xTicks[1].label === xTicks[0].label || xTicks[1].label === xTicks[2].label)) {
      xTicks.splice(1, 1);
    }
    if (xTicks[0].label === xTicks[xTicks.length - 1].label) xTicks = [xTicks[xTicks.length - 1]];

    return { xy, line, area, yTicks, xTicks, plotLeft, plotRight, plotTop, plotBottom };
  });

  readonly active = computed(() => {
    const index = this.activeIndex();
    const geometry = this.geometry();
    if (index === null || !geometry) return null;
    const point = this.sorted()[index];
    const { x, y } = geometry.xy[index];
    return {
      x,
      y,
      value: valueFormat(this.currency(), point.value).format(point.value),
      detail: point.daily ? `${formatDay(point.time)} close` : formatFull(point.time),
      flip: x > (geometry.plotLeft + geometry.plotRight) / 2,
    };
  });

  readonly summary = computed(() => {
    const points = this.sorted();
    if (points.length < 2) return `${this.label()} chart: not enough data yet`;
    const first = points[0];
    const last = points[points.length - 1];
    const when = (point: ValuePoint) => (point.daily ? formatDay(point.time) : formatFull(point.time));
    const money = (value: number) => valueFormat(this.currency(), value).format(value);
    return `${this.label()} from ${when(first)} to ${when(last)}, ${money(first.value)} to ${money(last.value)}`;
  });

  constructor() {
    const host = inject(ElementRef<HTMLElement>);
    const observer = new ResizeObserver(([entry]) => this.width.set(Math.floor(entry.contentRect.width)));
    observer.observe(host.nativeElement);
    inject(DestroyRef).onDestroy(() => observer.disconnect());
  }

  // The crosshair snaps to the nearest point, so the reader aims at a time, not the line
  onPointerMove(event: PointerEvent): void {
    const geometry = this.geometry();
    if (!geometry) return;
    const bounds = (event.currentTarget as SVGElement).getBoundingClientRect();
    const x = event.clientX - bounds.left;
    let nearest = 0;
    geometry.xy.forEach((point, i) => {
      if (Math.abs(point.x - x) < Math.abs(geometry.xy[nearest].x - x)) nearest = i;
    });
    this.activeIndex.set(nearest);
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.sorted().length;
    if (count === 0) return;
    const current = this.activeIndex() ?? count - 1;
    if (event.key === 'ArrowLeft') this.activeIndex.set(Math.max(0, current - 1));
    else if (event.key === 'ArrowRight') this.activeIndex.set(Math.min(count - 1, current + 1));
    else if (event.key === 'Escape') this.activeIndex.set(null);
    else return;
    event.preventDefault();
  }

  onFocus(): void {
    if (this.activeIndex() === null) this.activeIndex.set(this.sorted().length - 1);
  }

  clearActive(): void {
    this.activeIndex.set(null);
  }
}

// Round the y-axis to clean numbers (0 / 2,500 / 5,000 ...)
function niceScale(min: number, max: number, targetTicks = 4): { min: number; max: number; step: number; ticks: number[] } {
  if (min === max) {
    const pad = Math.abs(min) * 0.05 || 1;
    min -= pad;
    max += pad;
  }
  const rough = (max - min) / targetTicks;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const normalized = rough / magnitude;
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10) * magnitude;
  const lo = Math.floor(min / step) * step;
  const hi = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let value = lo; value <= hi + step / 2; value += step) ticks.push(Number(value.toFixed(10)));
  return { min: lo, max: hi, step, ticks };
}

function formatTime(time: number, span: number): string {
  const options: Intl.DateTimeFormatOptions =
    span < 36 * 60 * 60 * 1000
      ? { hour: 'numeric', minute: '2-digit' }
      : span < 400 * 24 * 60 * 60 * 1000
        ? { month: 'short', day: 'numeric' }
        : { month: 'short', year: 'numeric' };
  return new Intl.DateTimeFormat('en-US', options).format(time);
}

function formatDay(time: number): string {
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(time);
}

function formatFull(time: number): string {
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(time);
}
