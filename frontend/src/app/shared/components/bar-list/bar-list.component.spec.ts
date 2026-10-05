import { TestBed } from '@angular/core/testing';

import { BarListComponent } from './bar-list.component';

describe('BarListComponent', () => {
  it('scales bars to the largest value and keeps values readable as text', () => {
    const fixture = TestBed.createComponent(BarListComponent);
    fixture.componentRef.setInput('label', 'Spending by category');
    fixture.componentRef.setInput('items', [
      { label: 'Hotel', value: 100, display: '$100.00' },
      { label: 'Meals', value: 25, display: '$25.00' },
    ]);
    fixture.detectChanges();
    const fills = fixture.nativeElement.querySelectorAll('.bar-fill');

    expect(fixture.nativeElement.querySelector('ul').getAttribute('aria-label')).toBe('Spending by category');
    expect(fills[0].style.width).toBe('100%');
    expect(fills[1].style.width).toBe('25%');
    expect(fixture.nativeElement.textContent).toContain('$25.00');
  });

  it('shows the empty text when there is no data', () => {
    const fixture = TestBed.createComponent(BarListComponent);
    fixture.componentRef.setInput('label', 'x');
    fixture.componentRef.setInput('items', []);
    fixture.componentRef.setInput('emptyText', 'No expenses yet.');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No expenses yet.');
  });
});
