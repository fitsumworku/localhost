import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

// Must match the inline script in index.html, which applies the saved theme before Angular starts.
const STORAGE_KEY = 'theme';

// Light/dark mode. The choice lives in localStorage (per device, kept across logins) and is
// applied as data-theme on <html>, which switches the colour tokens in styles.scss.
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.current());

  toggle(): void {
    this.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this.theme.set(theme);
    document.documentElement.dataset['theme'] = theme;
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Storage blocked: the theme still applies until the page reloads
    }
  }

  private current(): Theme {
    return document.documentElement.dataset['theme'] === 'light' ? 'light' : 'dark';
  }
}
