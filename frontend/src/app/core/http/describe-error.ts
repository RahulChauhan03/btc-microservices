import { HttpErrorResponse } from '@angular/common/http';

/**
 * A user-facing explanation of a failed request that names the service involved, so "not running" (503 from
 * the gateway), "this build has no such endpoint" (404) and genuine validation errors can be told apart.
 */
export function describeHttpError(error: unknown, service: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return `The ${service} request failed.`;
  }
  switch (error.status) {
    case 0:
      return 'BTC Flow can’t be reached. Check your connection and that the API gateway is running.';
    case 503:
      return `The ${service} is unavailable (not running or not registered with service discovery).`;
    case 404:
      return `The ${service} doesn’t provide this data. It may be running an older version.`;
    case 403:
      return `You don’t have access to this ${service} data.`;
    default: {
      const message = error.error?.message;
      return message ? `${message} (${service}, HTTP ${error.status})` : `The ${service} answered HTTP ${error.status}.`;
    }
  }
}
