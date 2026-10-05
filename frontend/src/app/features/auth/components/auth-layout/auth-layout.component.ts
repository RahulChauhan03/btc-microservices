import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { BrandMarkComponent } from '../../../../shared/components/brand-mark/brand-mark.component';

/** Shared frame for sign-in and password recovery: brand panel + centred form area. */
@Component({
  selector: 'app-auth-layout',
  imports: [BrandMarkComponent, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './auth-layout.component.html',
  styleUrl: './auth-layout.component.css',
})
export class AuthLayoutComponent {
  readonly highlights = [
    { icon: 'flight_takeoff', text: 'Plan trips and keep every booking in one place' },
    { icon: 'receipt_long', text: 'Capture expenses as they happen, linked to the trip' },
    { icon: 'task_alt', text: 'Submit, review and approve claims with a clear audit trail' },
  ];
}
