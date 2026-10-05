import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { UserService } from '../../../../core/services/user.service';
import { EmployeeDirectoryComponent } from './employee-directory.component';

describe('EmployeeDirectoryComponent', () => {
  const users = { listUsers: vi.fn() };

  function render() {
    vi.clearAllMocks();
    users.listUsers.mockReturnValue(of({ items: [{ id: 3, name: 'Asha Rao', email: 'asha@x.test', role: 'EMPLOYEE', createdAt: '2026-01-01T00:00:00' }], total: 41 }));
    TestBed.configureTestingModule({
      imports: [EmployeeDirectoryComponent],
      providers: [provideRouter([]), { provide: UserService, useValue: users }],
    });
    const fixture = TestBed.createComponent(EmployeeDirectoryComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists people from the server with paging and links to profiles', () => {
    const el: HTMLElement = render().nativeElement;
    expect(users.listUsers).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20, sort: 'name,asc' }));
    expect(el.querySelector('a[href="/employees/3"]')?.textContent).toContain('Asha Rao');
    expect(el.textContent).toContain('page 1 of 3');
  });

  it('searches on the server after typing stops and filters by role', async () => {
    vi.useFakeTimers();
    const fixture = render();
    fixture.componentInstance.search.setValue('  asha ');
    vi.advanceTimersByTime(300);
    expect(users.listUsers).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'asha', page: 0 }));

    fixture.componentInstance.role.setValue('ADMIN');
    expect(users.listUsers).toHaveBeenLastCalledWith(expect.objectContaining({ role: 'ADMIN' }));
    vi.useRealTimers();
  });

  it('pages forward', () => {
    const fixture = render();
    fixture.componentInstance.load(1);
    expect(users.listUsers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
  });
});
