import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { LoadingService } from './core/services/loading.service';
import { BtcLoaderComponent } from './shared/components/btc-loader/btc-loader.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BtcLoaderComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class AppComponent {
  /** One global loader instance for the whole app, driven by the loading interceptor. */
  protected readonly loading = inject(LoadingService);
}
