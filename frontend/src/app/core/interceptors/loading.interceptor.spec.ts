import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { inlineFeedbackRequest, withLoaderMessage } from '../http/request-context';
import { LOADER_TIMING, LoadingService } from '../services/loading.service';
import { loadingInterceptor } from './loading.interceptor';

describe('loadingInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let loading: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([loadingInterceptor])),
        provideHttpClientTesting(),
        { provide: LOADER_TIMING, useValue: { showDelayMs: 0, minVisibleMs: 0 } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    loading = TestBed.inject(LoadingService);
  });

  afterEach(() => backend.verify());

  it('clears the loading state when a request succeeds', () => {
    http.get('/trips', { context: withLoaderMessage('Loading trips…') }).subscribe();
    expect(loading.isLoading()).toBe(true);
    expect(loading.message()).toBe('Loading trips…');

    backend.expectOne('/trips').flush([]);
    expect(loading.isLoading()).toBe(false);
  });

  it('clears the loading state when a request fails', () => {
    http.get('/trips').subscribe({ error: () => undefined });
    backend.expectOne('/trips').flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(loading.isLoading()).toBe(false);
  });

  it('clears the loading state when a request is cancelled', () => {
    const subscription = http.get('/trips').subscribe();
    subscription.unsubscribe();

    expect(loading.isLoading()).toBe(false);
    backend.expectOne('/trips');
  });

  it('keeps loading until every concurrent request has completed', () => {
    http.get('/trips').subscribe();
    http.get('/claims').subscribe({ error: () => undefined });

    backend.expectOne('/claims').flush(null, { status: 503, statusText: 'Unavailable' });
    expect(loading.isLoading()).toBe(true);

    backend.expectOne('/trips').flush([]);
    expect(loading.isLoading()).toBe(false);
  });

  it('does not count requests that show their own inline feedback', () => {
    http.post('/auth/login', {}, { context: inlineFeedbackRequest() }).subscribe();
    expect(loading.isLoading()).toBe(false);
    backend.expectOne('/auth/login').flush({});
  });
});
