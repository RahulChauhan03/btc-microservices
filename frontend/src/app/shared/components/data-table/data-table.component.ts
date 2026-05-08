import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
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
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
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
  @Input() minColumnWidth = 160;
  @Input() minTableWidth = 760;

  @Output() filterChange = new EventEmitter<{ key: string; value: string }>();
  @Output() cellSelectChange = new EventEmitter<{ row: T; column: DataTableColumn<T>; value: string }>();

  searchQuery = '';

  get displayedColumns(): string[] {
    return this.actions.length ? ['actions', ...this.columns.map((column) => column.key)] : this.columns.map((column) => column.key);
  }

  get tableMinWidth(): string {
    const actionWidth = this.actions.length ? 56 : 0;
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
