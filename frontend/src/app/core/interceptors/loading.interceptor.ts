import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';

import { LOADER_MESSAGE, SKIP_GLOBAL_LOADER } from '../http/request-context';
import { LoadingService } from '../services/loading.service';

export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_GLOBAL_LOADER)) {
    return next(req);
  }
  const loadingService = inject(LoadingService);
  loadingService.start(req.context.get(LOADER_MESSAGE));

  // finalize runs on success, error and unsubscribe, so the counter can never leak.
  return next(req).pipe(finalize(() => loadingService.stop()));
};
