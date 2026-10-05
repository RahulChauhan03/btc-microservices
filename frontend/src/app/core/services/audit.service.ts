import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuditEntry, AuditSource, PagedResult } from '../models/domain.models';
import { CrudHttpService, QueryParams } from './crud-http.service';

/** Administrator-only audit search. Each service keeps its own audit log, written with the audited change. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly crudHttp = inject(CrudHttpService);

  search(source: AuditSource, params: QueryParams): Observable<PagedResult<AuditEntry>> {
    return this.crudHttp.page<AuditEntry>(`${environment.apiBaseUrl}/${source}/audit-logs`, params);
  }
}
