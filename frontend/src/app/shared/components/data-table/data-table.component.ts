import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

export type DataTableColumnType = 'text' | 'date' | 'currency' | 'chip' | 'boolean' | 'select';

export interface DataTableColumn<T = any> {
  key: string;
  header: string;
  type?: DataTableColumnType;
  currencyCode?: string;
  value?: (row: T) => unknown;
  options?: DataTableFilterOption[];
}

export interface DataTableAction<T = any> {
  id: string;
  label: string;
  icon: string;
  handler?: (row: T) => void;
  /** Hides the action for rows where it does not apply. UX only: the backend enforces the rules. */
  visible?: (row: T) => boolean;
}

export interface DataTableFilterOption {
  label: string;
  value: string;
}

export interface DataTableFilter {
  key: string;
  label: string;
  value: string;
  options: DataTableFilterOption[];
}

@Component({
  selector: 'app-data-table',
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './data-table.component.html',
  styleUrl: './data-table.component.css',
})
export class DataTableComponent<T = any> {
  @Input({ required: true }) title = '';
  @Input() rows: T[] = [];
  @Input() columns: DataTableColumn<T>[] = [];
  @Input() actions: DataTableAction<T>[] = [];
  @Input() searchPlaceholder = 'Search';
  @Input() filters: DataTableFilter[] = [];
  @Input() showTitle = true;
  @Input() minColumnWidth = 160;
  @Input() minTableWidth = 760;
  @Input() emptyTitle = 'Nothing here yet';
  @Input() emptyMessage = 'Records you have access to will appear here.';

  @Output() filterChange = new EventEmitter<{ key: string; value: string }>();
  @Output() cellSelectChange = new EventEmitter<{ row: T; column: DataTableColumn<T>; value: string }>();

  searchQuery = '';

  get displayedColumns(): string[] {
    return this.actions.length ? [...this.columns.map((column) => column.key), 'actions'] : this.columns.map((column) => column.key);
  }

  get tableMinWidth(): string {
    const actionWidth = this.actions.length ? 236 : 0;
    const calculatedWidth = this.columns.length * this.minColumnWidth + actionWidth;
    return `${Math.max(this.minTableWidth, calculatedWidth)}px`;
  }

  get filteredRows(): T[] {
    const query = this.searchQuery.trim().toLowerCase();

    if (!query) {
      return this.rows;
    }

    return this.rows.filter((row) =>
      this.columns.some((column) => String(this.resolveValue(row, column) ?? '').toLowerCase().includes(query)),
    );
  }

  resolveValue(row: T, column: DataTableColumn<T>): unknown {
    if (column.value) {
      return column.value(row);
    }

    return (row as Record<string, unknown>)[column.key];
  }

  resolveDateValue(row: T, column: DataTableColumn<T>): string | number | Date | null | undefined {
    const value = this.resolveValue(row, column);
    return value instanceof Date || typeof value === 'string' || typeof value === 'number' ? value : null;
  }

  resolveCurrencyValue(row: T, column: DataTableColumn<T>): string | number | null | undefined {
    const value = this.resolveValue(row, column);
    return typeof value === 'string' || typeof value === 'number' ? value : null;
  }

  resolveBadgeClass(row: T, column: DataTableColumn<T>): string {
    const value = String(this.resolveValue(row, column) ?? '').toLowerCase().replace(/_/g, '-');
    return `status-badge status-${value}`;
  }


  /** Identifies a row for screen readers in action labels, e.g. "Delete T-100". */
  rowLabel(row: T): string {
    const first = this.columns[0];
    return first ? String(this.resolveValue(row, first) ?? '') : '';
  }

  runAction(action: DataTableAction<T>, row: T): void {
    action.handler?.(row);
  }

  selectFilter(filter: DataTableFilter, value: string): void {
    this.filterChange.emit({ key: filter.key, value });
  }

  selectCell(row: T, column: DataTableColumn<T>, value: string): void {
    this.cellSelectChange.emit({ row, column, value });
  }
}
