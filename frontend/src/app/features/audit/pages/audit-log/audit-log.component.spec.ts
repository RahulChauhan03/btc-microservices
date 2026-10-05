import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { AuditService } from '../../../../core/services/audit.service';
import { AuditLogComponent } from './audit-log.component';

describe('AuditLogComponent', () => {
  const service = { search: vi.fn() };

  function render() {
    vi.clearAllMocks();
    service.search.mockReturnValue(of({ items: [{ id: 1, source: 'claims', actorId: 1, action: 'CLAIM_APPROVED', targetType: 'CLAIM', targetId: 7, summary: 'Claim C-7 approved', createdAt: '2026-10-01T10:00:00' }], total: 1 }));
    TestBed.configureTestingModule({ imports: [AuditLogComponent], providers: [{ provide: AuditService, useValue: service }] });
    const fixture = TestBed.createComponent(AuditLogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('searches the claim audit log newest first and shows records', () => {
    const el: HTMLElement = render().nativeElement;
    expect(service.search).toHaveBeenCalledWith('claims', expect.objectContaining({ page: 0, sort: 'createdAt,desc' }));
    expect(el.textContent).toContain('Claim approved');
    expect(el.textContent).toContain('Claim C-7 approved');
  });

  it('explains a failing source with its service and the backend message', () => {
    const fixture = render();
    service.search.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { message: "Invalid value for 'id'" } })));
    fixture.componentInstance.load(0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')?.textContent).toContain("Invalid value for 'id' (claims service, HTTP 400)");
  });

  it('validates the actor id and date order before searching', () => {
    const fixture = render();
    service.search.mockClear();
    fixture.componentInstance.filters.patchValue({ actorId: 'abc' });
    fixture.componentInstance.load(0);
    fixture.componentInstance.filters.patchValue({ actorId: '', from: '2026-05-01', to: '2026-01-01' });
    fixture.componentInstance.load(0);
    fixture.detectChanges();
    expect(service.search).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('must be on or before');
  });

  it('switches source and passes filters to the backend', () => {
    const fixture = render();
    fixture.componentInstance.setSource('users');
    fixture.componentInstance.filters.patchValue({ action: 'USER_ROLE_CHANGED', actorId: ' 4 ', from: '2026-01-01', to: '2026-02-01' });
    fixture.componentInstance.load(0);
    expect(service.search).toHaveBeenLastCalledWith('users', expect.objectContaining({ action: 'USER_ROLE_CHANGED', actorId: '4', from: '2026-01-01', to: '2026-02-01' }));
  });
});
