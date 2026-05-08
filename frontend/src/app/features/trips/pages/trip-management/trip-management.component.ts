import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { Trip, TripPayload } from '../../../../core/models/domain.models';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ToastService } from '../../../../core/services/toast.service';
import { TripService } from '../../../../core/services/trip.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
} from '../../../../shared/components/data-table/data-table.component';
import { DatepickerHeaderComponent } from '../../../../shared/components/datepicker-header/datepicker-header.component';

@Component({
  selector: 'app-trip-management',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    DataTableComponent,
  ],
  templateUrl: './trip-management.component.html',
  styleUrl: './trip-management.component.css',
})
export class TripManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly tripService = inject(TripService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);
  readonly today = this.startOfDay(new Date());
  readonly calendarHeaderComponent = DatepickerHeaderComponent;

  readonly trips = signal<Trip[]>([]);
  readonly editingTripId = signal<number | null>(null);
  readonly tableColumns: DataTableColumn<Trip>[] = [
    { key: 'destination', header: 'Destination' },
    { key: 'purpose', header: 'Purpose' },
    { key: 'dates', header: 'Dates', value: (trip) => `${trip.startDate} - ${trip.endDate}` },
    { key: 'estimatedBudget', header: 'Budget', type: 'currency' },
    { key: 'status', header: 'Status', type: 'chip' },
  ];
  readonly tableActions: DataTableAction<Trip>[] = [
    { id: 'view', label: 'View', icon: 'visibility', handler: (trip) => this.viewTrip(trip) },
    { id: 'edit', label: 'Edit', icon: 'edit', handler: (trip) => this.editTrip(trip) },
    { id: 'delete', label: 'Delete', icon: 'delete', handler: (trip) => this.deleteTrip(trip) },
  ];

  readonly tripForm = this.fb.nonNullable.group({
    destination: ['', [Validators.required, Validators.minLength(3)]],
    purpose: ['', [Validators.required, Validators.minLength(5)]],
    startDate: ['' as string | Date, [Validators.required, this.minDateValidator(() => this.today)]],
    endDate: ['' as string | Date, [Validators.required, this.minDateValidator(() => this.startDateMin())]],
    estimatedBudget: [0, [Validators.required, Validators.min(1)]],
    status: ['PLANNED' as Trip['status'], Validators.required],
  });

  constructor() {
    this.loadTrips();
    this.tripForm.controls.startDate.valueChanges.subscribe(() => {
      this.tripForm.controls.endDate.updateValueAndValidity();
    });
  }

  loadTrips(): void {
    this.tripService.getTrips((trips) => this.trips.set(trips));
  }

  submit(): void {
    if (this.tripForm.invalid) {
      this.tripForm.markAllAsTouched();
      return;
    }

    const formValue = this.tripForm.getRawValue();
    const payload: TripPayload = {
      destination: formValue.destination,
      purpose: formValue.purpose,
      startDate: this.toDateString(formValue.startDate),
      endDate: this.toDateString(formValue.endDate),
      estimatedBudget: formValue.estimatedBudget,
      status: formValue.status,
    };
    const onSaved = () => {
      this.toastService.success(this.editingTripId() ? TOAST_MESSAGES.trips.updated : TOAST_MESSAGES.trips.created);
      this.resetForm();
      this.loadTrips();
    };

    if (this.editingTripId()) {
      this.tripService.updateTrip(this.editingTripId() as number, payload, onSaved);
      return;
    }

    this.tripService.createTrip(payload, onSaved);
  }

  editTrip(trip: Trip): void {
    this.editingTripId.set(trip.id);
    this.tripForm.patchValue({
      destination: trip.destination,
      purpose: trip.purpose,
      startDate: this.toDate(trip.startDate) ?? '',
      endDate: this.toDate(trip.endDate) ?? '',
      estimatedBudget: trip.estimatedBudget,
      status: trip.status,
    });
  }

  viewTrip(trip: Trip): void {
    this.toastService.info(TOAST_MESSAGES.trips.viewed(trip));
  }

  deleteTrip(trip: Trip): void {
    this.confirmationService
      .confirmDelete({
        tableName: 'Trip Requests',
        columnName: 'Destination',
        value: trip.destination,
      })
      .subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      this.tripService.deleteTrip(trip.id, () => {
        this.toastService.success(TOAST_MESSAGES.trips.deleted);
        if (this.editingTripId() === trip.id) {
          this.resetForm();
        }
        this.loadTrips();
      });
      });
  }

  resetForm(): void {
    this.editingTripId.set(null);
    this.tripForm.reset({
      destination: '',
      purpose: '',
      startDate: '',
      endDate: '',
      estimatedBudget: 0,
      status: 'PLANNED',
    });
  }

  startDateMin(): Date {
    const startDate = this.toDate(this.tripForm.controls.startDate.value);
    return startDate && startDate > this.today ? startDate : this.today;
  }

  private minDateValidator(minDate: () => Date): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = this.toDate(control.value);

      if (!value) {
        return null;
      }

      return value < minDate() ? { minDate: true } : null;
    };
  }

  private toDate(value: string | Date | null | undefined): Date | null {
    if (!value) {
      return null;
    }

    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? null : this.startOfDay(date);
  }

  private toDateString(value: string | Date): string {
    const date = this.toDate(value);

    if (!date) {
      return '';
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private startOfDay(date: Date): Date {
    const day = new Date(date);
    day.setHours(0, 0, 0, 0);
    return day;
  }
}
