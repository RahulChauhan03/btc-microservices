import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('keeps both password recovery screens available without a session guard', () => {
    for (const path of ['forgot-password', 'reset-password']) {
      expect(routes.find((route) => route.path === path)?.canActivate).toBeUndefined();
    }
  });
});
