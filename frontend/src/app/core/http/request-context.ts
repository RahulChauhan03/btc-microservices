import { HttpContext, HttpContextToken } from '@angular/common/http';

/** Requests that already show their own feedback (e.g. a busy button) skip the global loader. */
export const SKIP_GLOBAL_LOADER = new HttpContextToken<boolean>(() => false);

/** Requests whose errors are shown inline by the page skip the global error toast. */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/** Optional status text for the global loader while this request is pending. */
export const LOADER_MESSAGE = new HttpContextToken<string | null>(() => null);

export function withLoaderMessage(message: string): HttpContext {
  return new HttpContext().set(LOADER_MESSAGE, message);
}

/** For forms that show their own progress and errors (sign-in, password recovery). */
export function inlineFeedbackRequest(): HttpContext {
  return new HttpContext().set(SKIP_GLOBAL_LOADER, true).set(SKIP_ERROR_TOAST, true);
}
