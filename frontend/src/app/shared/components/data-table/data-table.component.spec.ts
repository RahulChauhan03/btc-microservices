import { TestBed } from '@angular/core/testing';

import { DataTableComponent, DataTableColumn } from './data-table.component';

interface Row {
  id: number;
  name: string;
}

describe('DataTableComponent pagination', () => {
  function render(
    rows: Row[],
    columns: DataTableColumn<Row>[],
    configure?: (component: DataTableComponent<Row>) => void,
  ) {
    TestBed.configureTestingModule({ imports: [DataTableComponent] });
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentInstance.title = 'Records';
    fixture.componentInstance.rows = rows;
    fixture.componentInstance.columns = columns;
    configure?.(fixture.componentInstance);
    fixture.detectChanges();
    return fixture;
  }

  it('paginates client-side rows and resets to the first page when page size changes', () => {
    const rows = Array.from({ length: 25 }, (_, index) => ({ id: index + 1, name: `Row ${index + 1}` }));
    const fixture = render(rows, [{ key: 'name', header: 'Name' }]);
    const dataRows = () => fixture.nativeElement.querySelectorAll('tbody tr.mat-mdc-row').length;

    expect(dataRows()).toBe(20);
    fixture.nativeElement.querySelector('button[aria-label="Next page"]').click();
    fixture.detectChanges(false);
    expect(dataRows()).toBe(5);
    expect(fixture.nativeElement.querySelector('.page-count').textContent.trim()).toBe('2 / 2');

    const pageSize = fixture.nativeElement.querySelector('.page-size-control select') as HTMLSelectElement;
    pageSize.value = '10';
    pageSize.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(dataRows()).toBe(10);
    expect(fixture.nativeElement.querySelector('.page-count').textContent.trim()).toBe('1 / 3');
  });

  it('keeps server rows intact and emits page and page-size changes', () => {
    const fixture = render(
      [{ id: 21, name: 'Row 21' }, { id: 22, name: 'Row 22' }],
      [{ key: 'name', header: 'Name' }],
      (component) => {
        component.paginationMode = 'server';
        component.totalItems = 42;
        component.pageIndex = 1;
      },
    );
    const component = fixture.componentInstance;
    const changed = vi.fn();
    component.pageChange.subscribe(changed);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr.mat-mdc-row').length).toBe(2);
    fixture.nativeElement.querySelector('button[aria-label="Next page"]').click();
    fixture.detectChanges(false);
    expect(changed).toHaveBeenLastCalledWith({ pageIndex: 2, pageSize: 20 });

    component.changePageSize('50');
    expect(changed).toHaveBeenLastCalledWith({ pageIndex: 0, pageSize: 50 });
  });

  it('shows loading, errors, and a useful empty state without an empty paginator', () => {
    const fixture = render([], [{ key: 'name', header: 'Name' }]);
    expect(fixture.nativeElement.textContent).toContain('Nothing here yet');
    expect(fixture.nativeElement.querySelector('.table-pagination')).toBeNull();

    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading records');

    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('errorMessage', 'Network unavailable');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Network unavailable');
  });
});
