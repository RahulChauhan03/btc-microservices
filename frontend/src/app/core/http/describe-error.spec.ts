import { HttpErrorResponse } from '@angular/common/http';

import { describeHttpError } from './describe-error';

describe('describeHttpError', () => {
  it('names the service and distinguishes outages, missing endpoints and validation errors', () => {
    expect(describeHttpError(new HttpErrorResponse({ status: 503 }), 'claims service')).toContain('claims service is unavailable');
    expect(describeHttpError(new HttpErrorResponse({ status: 404 }), 'claims service')).toContain('older version');
    expect(describeHttpError(new HttpErrorResponse({ status: 0 }), 'claims service')).toContain('can’t be reached');
    expect(describeHttpError(new HttpErrorResponse({ status: 400, error: { message: "Invalid value for 'id'" } }), 'claims service'))
      .toBe("Invalid value for 'id' (claims service, HTTP 400)");
    expect(describeHttpError(new Error('x'), 'claims service')).toBe('The claims service request failed.');
  });
});
