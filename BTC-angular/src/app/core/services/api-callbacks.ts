import { Observable } from 'rxjs';

export type ApiSuccess<T> = (value: T) => void;
export type ApiError = (error: unknown) => void;

export function runRequest<T>(
  request$: Observable<T>,
  onSuccess?: ApiSuccess<T>,
  onError?: ApiError,
): void {
  request$.subscribe({
    next: (value) => onSuccess?.(value),
    error: (error) => onError?.(error),
  });
}
