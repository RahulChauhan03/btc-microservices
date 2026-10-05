import { TestBed } from '@angular/core/testing';

import { DEFAULT_LOADER_MESSAGE, LOADER_TIMING, LoadingService } from './loading.service';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [{ provide: LOADER_TIMING, useValue: { showDelayMs: 200, minVisibleMs: 400 } }],
    });
    service = TestBed.inject(LoadingService);
  });

  afterEach(() => vi.useRealTimers());

  it('never shows the loader for requests faster than the show delay', () => {
    service.start();
    vi.advanceTimersByTime(150);
    service.stop();
    vi.advanceTimersByTime(1000);

    expect(service.visible()).toBe(false);
    expect(service.isLoading()).toBe(false);
  });

  it('shows after the delay and stays for the minimum duration', () => {
    service.start('Loading trips…');
    vi.advanceTimersByTime(200);
    expect(service.visible()).toBe(true);
    expect(service.message()).toBe('Loading trips…');

    vi.advanceTimersByTime(50);
    service.stop();
    vi.advanceTimersByTime(300);
    expect(service.visible()).toBe(true);

    vi.advanceTimersByTime(50);
    expect(service.visible()).toBe(false);
    expect(service.message()).toBe(DEFAULT_LOADER_MESSAGE);
  });

  it('stays visible until the last concurrent request finishes, with a single overlay state', () => {
    service.start();
    service.start();
    vi.advanceTimersByTime(250);
    expect(service.visible()).toBe(true);

    service.stop();
    vi.advanceTimersByTime(1000);
    expect(service.visible()).toBe(true);
    expect(service.isLoading()).toBe(true);

    service.stop();
    vi.advanceTimersByTime(1000);
    expect(service.visible()).toBe(false);
  });

  it('ignores extra stop calls instead of going negative', () => {
    service.stop();
    service.start();
    vi.advanceTimersByTime(250);
    service.stop();
    vi.advanceTimersByTime(1000);

    expect(service.isLoading()).toBe(false);
    expect(service.visible()).toBe(false);
  });
});
