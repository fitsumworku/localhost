declare module 'rxjs' {
  export function forkJoin<T>(sources: T): Observable<T>;

  export interface Observable<T> {
    pipe(...operators: unknown[]): Observable<T>;
    subscribe(observer?: any): any;
  }

  export class Observable<T> {
    constructor(subscribe?: (this: Observable<T>, subscriber: any) => void | (() => void));
    subscribe(observer?: any): any;
  }
}
