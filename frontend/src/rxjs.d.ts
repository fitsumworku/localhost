declare module 'rxjs' {
  export interface Observable<T> {
    subscribe(observer?: any): any;
  }

  export class Observable<T> {
    constructor(subscribe?: (this: Observable<T>, subscriber: any) => void | (() => void));
    subscribe(observer?: any): any;
  }
}
