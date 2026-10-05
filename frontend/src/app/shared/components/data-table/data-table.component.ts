import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { BtcLoaderComponent } from '../btc-loader/btc-loader.component';

export type DataTableColumnType = 'text' | 'date' | 'currency' | 'chip' | 'boolean' | 'select' | 'link';
export type DataTablePaginationMode = 'client' | 'server';

export interface DataTablePageChange {
  pageIndex: number;
  pageSize: number;
}

export interface DataTableColumn<T = any> {
  key: string;
  header: string;
  type?: DataTableColumnType;
  currencyCode?: string;
  value?: (row: T) => unknown;
  options?: DataTableFilterOption[];
  link?: (row: T) => string | unknown[];
  mobilePriority?: 'primary' | 'secondary';
  emptyValue?: string;
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
    RouterLink,
    BtcLoaderComponent,
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
  @Input() showSearch = true;
  @Input() showPagination = true;
  @Input() serverSearch = false;
  @Input() paginationMode: DataTablePaginationMode = 'client';
  @Input() totalItems = 0;
  @Input() pageIndex = 0;
  @Input() pageSize = 20;
  @Input() pageSizeOptions = [10, 20, 50];
  @Input() loading = false;
  @Input() errorMessage: string | null = null;
  @Input() minColumnWidth = 160;
  @Input() minTableWidth = 760;
  @Input() emptyTitle = 'Nothing here yet';
  @Input() emptyMessage = 'Records you have access to will appear here.';

  @Output() filterChange = new EventEmitter<{ key: string; value: string }>();
  @Output() cellSelectChange = new EventEmitter<{ row: T; column: DataTableColumn<T>; value: string }>();
  @Output() pageChange = new EventEmitter<DataTablePageChange>();
  @Output() searchChange = new EventEmitter<string>();
  @Output() retry = new EventEmitter<void>();

  searchQuery = '';
  private clientPageIndex = 0;
  private clientPageSize = 20;
  private filteredSourceRows: T[] | null = null;
  private filteredQuery = '';
  private filteredRowsCache: T[] = [];
  private visibleSourceRows: T[] | null = null;
  private visiblePageIndex = -1;
  private visiblePageSize = -1;
  private visibleRowsCache: T[] = [];

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
    if (this.filteredSourceRows === this.rows && this.filteredQuery === query) {
      return this.filteredRowsCache;
    }

    this.filteredSourceRows = this.rows;
    this.filteredQuery = query;

    if (!query) {
      this.filteredRowsCache = this.rows;
      return this.filteredRowsCache;
    }

    this.filteredRowsCache = this.rows.filter((row) =>
      this.columns.some((column) => String(this.resolveValue(row, column) ?? '').toLowerCase().includes(query)),
    );
    return this.filteredRowsCache;
  }

  get visibleRows(): T[] {
    const filteredRows = this.filteredRows;
    const pageIndex = this.activePageIndex;
    const pageSize = this.activePageSize;
    if (this.paginationMode === 'server') {
      return filteredRows;
    }
    if (
      this.visibleSourceRows === filteredRows &&
      this.visiblePageIndex === pageIndex &&
      this.visiblePageSize === pageSize
    ) {
      return this.visibleRowsCache;
    }
    this.visibleSourceRows = filteredRows;
    this.visiblePageIndex = pageIndex;
    this.visiblePageSize = pageSize;
    const start = pageIndex * pageSize;
    this.visibleRowsCache = filteredRows.slice(start, start + pageSize);
    return this.visibleRowsCache;
  }

  get activePageIndex(): number {
    return this.paginationMode === 'server' ? this.pageIndex : this.clientPageIndex;
  }

  get activePageSize(): number {
    return this.paginationMode === 'server' ? this.pageSize : this.clientPageSize;
  }

  get paginationTotal(): number {
    if (this.paginationMode === 'server') {
      return this.totalItems;
    }
    return this.filteredRows.length;
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.paginationTotal / this.activePageSize));
  }

  get rangeStart(): number {
    if (this.paginationTotal === 0) {
      return 0;
    }
    return this.activePageIndex * this.activePageSize + 1;
  }

  get rangeEnd(): number {
    return Math.min(this.activePageIndex * this.activePageSize + this.visibleRows.length, this.paginationTotal);
  }

  get matchesOnPage(): number {
    return this.serverSearch ? this.totalItems : this.filteredRows.length;
  }

  get showPager(): boolean {
    return this.showPagination && this.paginationTotal > 0 && !this.loading && !this.errorMessage;
  }

  updateSearch(value: string): void {
    this.searchQuery = value;
    this.clientPageIndex = 0;
    if (this.serverSearch) {
      this.searchChange.emit(value);
    }
  }

  changePage(pageIndex: number): void {
    const next = Math.max(0, Math.min(pageIndex, this.pageCount - 1));
    this.emitPage(next, this.activePageSize);
  }

  changePageSize(value: string): void {
    const pageSize = Number(value);
    if (!Number.isFinite(pageSize) || pageSize < 1) {
      return;
    }
    this.emitPage(0, pageSize);
  }

  private emitPage(pageIndex: number, pageSize: number): void {
    if (this.paginationMode === 'server') {
      this.pageChange.emit({ pageIndex, pageSize });
      return;
    }
    this.clientPageIndex = pageIndex;
    this.clientPageSize = pageSize;
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

  resolveLink(row: T, column: DataTableColumn<T>): string | unknown[] | null {
    return column.link?.(row) ?? null;
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
