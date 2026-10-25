import { HttpError } from './http.ts';
import { wait } from './wait.ts';

/** How often and how patiently to retry. */
export interface RetryPolicy { retries: number; backoffMs: number }

/** Run `fn`, retrying retryable `HttpError`s with exponential backoff. */
export async function withRetry<T>(fn: () => Promise<T>, policy: RetryPolicy, onRetry: () => void = () => {}): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    try {
      return await fn();
    } catch (err) {
      if (!(err instanceof HttpError) || !err.retryable || attempt >= policy.retries) throw err;
      onRetry();
      await wait(policy.backoffMs * 2 ** attempt);
    }
  }
}
