import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TOAST_MESSAGES } from '../../../../core/constants/toast-messages';
import { describeHttpError } from '../../../../core/http/describe-error';
import { Trip, TripPayload } from '../../../../core/models/domain.models';
import { ConfirmationService } from '../../../../core/services/confirmation.service';
import { ToastService } from '../../../../core/services/toast.service';
import { AuthService } from '../../../../core/services/auth.service';
import { TripService } from '../../../../core/services/trip.service';
import {
  DataTableAction,
  DataTableColumn,
  DataTableComponent,
  DataTablePageChange,
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
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    DataTableComponent,
  ],
  templateUrl: './trip-management.component.html',
  styleUrl: './trip-management.component.css',
})
export class TripManagementComponent {
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tripService = inject(TripService);
  private readonly authService = inject(AuthService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toastService = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly today = this.startOfDay(new Date());
  readonly calendarHeaderComponent = DatepickerHeaderComponent;

  readonly trips = signal<Trip[]>([]);
  readonly tableTotal = signal(0);
  readonly tablePage = signal(0);
  readonly tablePageSize = signal(20);
  readonly tableLoading = signal(false);
  readonly tableError = signal<string | null>(null);
  readonly editingTripId = signal<number | null>(null);
  readonly isFormPage = signal(false);
  private requestedEditId: number | null = null;
  readonly tableColumns: DataTableColumn<Trip>[] = [
    { key: 'tripCode', header: 'Trip Code' },
    { key: 'destination', header: 'Destination' },
    { key: 'dates', header: 'Dates', value: (trip) => `${trip.startDate} - ${trip.endDate}`, mobilePriority: 'secondary' },
    { key: 'budget', header: 'Budget', type: 'currency', mobilePriority: 'secondary' },
    { key: 'status', header: 'Status', type: 'chip' },
  ];
  readonly tableActions: DataTableAction<Trip>[] = [
    {
      id: 'view',
      label: 'View',
      icon: 'visibility',
      handler: (trip) => this.router.navigate(['/trips/view', trip.id]),
    },
    // Only owners may change a trip (administrators can view all). The backend enforces this too.
    {
      id: 'edit',
      label: 'Edit',
      icon: 'edit',
      handler: (trip) => this.editTrip(trip),
      visible: (trip) => this.isMine(trip),
    },
    {
      id: 'delete',
      label: 'Delete',
      icon: 'delete',
      handler: (trip) => this.deleteTrip(trip),
      visible: (trip) => this.isMine(trip),
    },
  ];

  readonly tripForm = this.fb.nonNullable.group({
    tripCode: ['', [Validators.required, Validators.minLength(3)]],
    destination: ['', [Validators.required, Validators.minLength(3)]],
    startDate: ['' as string | Date, [Validators.required, this.minDateValidator(() => this.today)]],
    endDate: ['' as string | Date, [Validators.required, this.minDateValidator(() => this.startDateMin())]],
    budget: [0, [Validators.required, Validators.min(1)]],
    status: ['PLANNED' as Trip['status'], Validators.required],
  });

  constructor() {
    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));
      this.isFormPage.set(this.router.url.includes('/new') || this.router.url.includes('/edit/'));
      this.editingTripId.set(Number.isFinite(id) && id > 0 ? id : null);
      this.patchEditingTrip();
    });
    if (!this.isFormPage()) {
      this.loadTrips();
    }
    this.tripForm.controls.startDate.valueChanges.subscribe(() => {
      this.tripForm.controls.endDate.updateValueAndValidity();
    });
  }

  loadTrips(page = this.tablePage(), size = this.tablePageSize()): void {
    this.tableLoading.set(true);
    this.tableError.set(null);
    this.tripService
      .listTrips({ page, size, sort: 'startDate,desc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          const lastPage = Math.max(0, Math.ceil(result.total / size) - 1);
          if (page > lastPage) {
            this.loadTrips(lastPage, size);
            return;
          }
          this.trips.set(result.items);
          this.tableTotal.set(result.total);
          this.tablePage.set(page);
          this.tablePageSize.set(size);
          this.tableLoading.set(false);
          this.patchEditingTrip();
        },
        error: (error: unknown) => {
          this.tableError.set(describeHttpError(error, 'trip service'));
          this.tableLoading.set(false);
        },
      });
  }

  onTablePageChange(event: DataTablePageChange): void {
    this.loadTrips(event.pageIndex, event.pageSize);
  }

  submit(): void {
    if (this.tripForm.invalid) {
      this.tripForm.markAllAsTouched();
      return;
    }

    const formValue = this.tripForm.getRawValue();
    const payload: TripPayload = {
      tripCode: formValue.tripCode,
      destination: formValue.destination,
      startDate: this.toDateString(formValue.startDate),
      endDate: this.toDateString(formValue.endDate),
      budget: formValue.budget,
      status: formValue.status,
    };
    const onSaved = () => {
      this.toastService.success(this.editingTripId() ? TOAST_MESSAGES.trips.updated : TOAST_MESSAGES.trips.created);
      this.resetForm();
      this.loadTrips();
      this.router.navigate(['/trips']);
    };

    if (this.editingTripId()) {
      this.tripService.updateTrip(this.editingTripId() as number, payload, onSaved);
      return;
    }

    this.tripService.createTrip(payload, onSaved);
  }

  private isMine(trip: Trip): boolean {
    return trip.ownerId !== null && trip.ownerId === this.authService.currentUser()?.id;
  }

  editTrip(trip: Trip): void {
    this.router.navigate(['/trips/edit', trip.id]);
  }

  private patchEditingTrip(): void {
    const trip = this.trips().find((item) => item.id === this.editingTripId());

    if (!trip) {
      if (this.isFormPage() && !this.editingTripId()) {
        this.resetForm();
      } else if (this.isFormPage() && this.editingTripId() && this.requestedEditId !== this.editingTripId()) {
        const id = this.editingTripId()!;
        this.requestedEditId = id;
        this.tripService.getTrip(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: (editingTrip) => this.patchForm(editingTrip),
        });
      }
      return;
    }

    this.patchForm(trip);
  }

  private patchForm(trip: Trip): void {
    this.tripForm.patchValue({
      tripCode: trip.tripCode,
      destination: trip.destination,
      startDate: this.toDate(trip.startDate) ?? '',
      endDate: this.toDate(trip.endDate) ?? '',
      budget: trip.budget,
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
        this.loadTrips(this.tablePage(), this.tablePageSize());
      });
      });
  }

  resetForm(): void {
    this.tripForm.reset({
      tripCode: '',
      destination: '',
      startDate: '',
      endDate: '',
      budget: 0,
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
