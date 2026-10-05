import { TestBed } from '@angular/core/testing';

import { BtcLoaderComponent } from './btc-loader.component';

describe('BtcLoaderComponent', () => {
  it('announces its message politely as a status', () => {
    const fixture = TestBed.createComponent(BtcLoaderComponent);
    fixture.componentRef.setInput('message', 'Loading claims…');
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;

    expect(host.getAttribute('role')).toBe('status');
    expect(host.getAttribute('aria-live')).toBe('polite');
    expect(host.textContent).toContain('Loading claims…');
    expect(host.classList).toContain('inline');
    expect(host.querySelector('.progress-line')).toBeNull();
  });

  it('renders the full-page variant with a progress line', () => {
    const fixture = TestBed.createComponent(BtcLoaderComponent);
    fixture.componentRef.setInput('mode', 'overlay');
    fixture.detectChanges();

    expect(fixture.nativeElement.classList).toContain('overlay');
    expect(fixture.nativeElement.querySelector('.progress-line')).not.toBeNull();
  });
});
