import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { inlineFeedbackRequest } from '../http/request-context';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  const authenticated = { value: false };
  const auth = { isAuthenticated: () => authenticated.value, logout: vi.fn() };
  const toast = { error: vi.fn() };
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  function fail401(context = inlineFeedbackRequest()): void {
    http.get('/x', { context }).subscribe({ error: () => undefined });
    backend.expectOne('/x').flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
  }

  it('ends an authenticated session on 401', () => {
    authenticated.value = true;
    fail401();
    expect(auth.logout).toHaveBeenCalled();
  });

  it('does not log out or redirect a signed-out visitor on 401', () => {
    authenticated.value = false;
    fail401();
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('skips the toast for requests that show errors inline, and toasts otherwise', () => {
    fail401();
    expect(toast.error).not.toHaveBeenCalled();

    http.get('/y').subscribe({ error: () => undefined });
    backend.expectOne('/y').flush({ message: 'Trip not found' }, { status: 404, statusText: 'Not Found' });
    expect(toast.error).toHaveBeenCalledWith('Trip not found');
  });
});
