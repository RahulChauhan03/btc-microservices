import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

interface ConfirmDialogData {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="dialog-head">
      <span class="dialog-icon" aria-hidden="true"><mat-icon>delete_outline</mat-icon></span>
      <h2 mat-dialog-title>{{ data.title }}</h2>
    </div>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      <p class="dialog-note">This action cannot be undone.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" (click)="close(false)" cdkFocusInitial>
        {{ data.cancelText ?? 'Cancel' }}
      </button>
      <button mat-flat-button type="button" class="danger-button" (click)="close(true)">
        {{ data.confirmText ?? 'Delete' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dialog-head {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 1.25rem 1.5rem 0;
      }

      .dialog-head h2 {
        margin: 0;
        padding: 0;
        font-size: 1.0625rem;
        font-weight: 650;
      }

      .dialog-head h2::before {
        display: none;
      }

      .dialog-icon {
        display: grid;
        place-items: center;
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: var(--danger-soft);
        color: var(--danger);
        flex: none;
      }

      mat-dialog-content p {
        margin: 0;
        color: var(--text-muted);
      }

      mat-dialog-content .dialog-note {
        margin-top: 0.5rem;
        font-size: 0.8125rem;
        color: var(--text-subtle);
      }

      mat-dialog-actions {
        gap: 0.5rem;
        padding: 0.5rem 1.5rem 1.25rem !important;
      }

      .danger-button:not(:disabled) {
        background: var(--danger) !important;
        color: #fff !important;
      }

      .danger-button:not(:disabled):hover {
        background: #912018 !important;
      }
    `,
  ],
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmDialogComponent, boolean>);

  close(result: boolean): void {
    this.dialogRef.close(result);
  }
}
