import { Component } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterOutlet } from '@angular/router';

import { LoadingOverlayComponent } from './shared/components/loading-overlay/loading-overlay.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, LoadingOverlayComponent, MatDialogModule, MatSnackBarModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class AppComponent {}
