import { Injectable, Injector, inject } from '@angular/core';

type ToastType = 'info' | 'success' | 'error';

/**
 * Toast notifications. The Material snack-bar code is loaded on the first toast rather than at startup,
 * which keeps it out of the initial (sign-in) bundle.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly injector = inject(Injector);

  info(message: string): void {
    this.show(message, 'info', 3500);
  }

  success(message: string): void {
    this.show(message, 'success', 3000);
  }

  error(message: string): void {
    this.show(message, 'error', 4500);
  }

  private show(message: string, type: ToastType, duration: number): void {
    import('@angular/material/snack-bar').then(({ MatSnackBar }) =>
      this.injector.get(MatSnackBar).open(message, 'Close', {
        duration,
        horizontalPosition: 'right',
        verticalPosition: 'top',
        panelClass: ['app-toast', `${type}-snackbar`],
        politeness: type === 'error' ? 'assertive' : 'polite',
      }),
    );
  }
}
