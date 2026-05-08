import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';

import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class ConfirmationService {
  private readonly dialog = inject(MatDialog);

  confirmDelete(options: {
    tableName: string;
    columnName: string;
    value: string;
  }): Observable<boolean | undefined> {
    const { tableName, columnName, value } = options;

    return this.dialog
      .open(ConfirmDialogComponent, {
        width: '420px',
        maxWidth: 'calc(100vw - 2rem)',
        data: {
          title: 'Confirm Delete',
          message: `Are you sure you want to delete "${value}" from the ${tableName} table?`,
          confirmText: 'Delete',
          cancelText: 'Cancel',
        },
      })
      .afterClosed();
  }
}
