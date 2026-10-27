import { Component, computed } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ThemeService } from './theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <button
      type="button"
      class="theme-toggle"
      (click)="themeService.toggle()"
      [attr.aria-label]="label()"
      [title]="label()"
    >
      <mat-icon>{{ themeService.theme() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon>
    </button>
  `,
  styleUrl: './theme-toggle.component.scss',
})
export class ThemeToggleComponent {
  readonly label = computed(() =>
    this.themeService.theme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode',
  );

  constructor(protected themeService: ThemeService) {}
}
