import { ChangeDetectorRef, Component, OnDestroy, inject } from '@angular/core';
import { DateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCalendar } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-datepicker-header',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './datepicker-header.component.html',
  styleUrl: './datepicker-header.component.css',
})
export class DatepickerHeaderComponent implements OnDestroy {
  private readonly calendar = inject<MatCalendar<Date>>(MatCalendar);
  private readonly dateAdapter = inject<DateAdapter<Date>>(DateAdapter);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);
  private readonly destroyed = new Subject<void>();

  constructor() {
    this.calendar.stateChanges.pipe(takeUntil(this.destroyed)).subscribe(() => {
      this.changeDetectorRef.markForCheck();
    });
  }

  get monthLabel(): string {
    return this.dateAdapter.getMonthNames('short')[this.dateAdapter.getMonth(this.calendar.activeDate)];
  }

  get yearLabel(): string {
    return this.dateAdapter.getYearName(this.calendar.activeDate);
  }

  previous(): void {
    this.moveCalendar(-1);
  }

  next(): void {
    this.moveCalendar(1);
  }

  openMonthView(): void {
    this.calendar.currentView = 'year';
  }

  openYearView(): void {
    this.calendar.currentView = 'multi-year';
  }

  ngOnDestroy(): void {
    this.destroyed.next();
    this.destroyed.complete();
  }

  private moveCalendar(direction: -1 | 1): void {
    const activeDate = this.calendar.activeDate;

    if (this.calendar.currentView === 'month') {
      this.calendar.activeDate = this.dateAdapter.addCalendarMonths(activeDate, direction);
      return;
    }

    this.calendar.activeDate = this.dateAdapter.addCalendarYears(
      activeDate,
      this.calendar.currentView === 'year' ? direction : direction * 24,
    );
  }
}
