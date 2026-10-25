/** A failed HTTP request, with the status when the server answered. */
export class HttpError extends Error {
  readonly status: number;
  readonly url: string;

  constructor(url: string, status: number, message: string) {
    super(`${status || 'network'} ${url}: ${message}`);
    this.url = url;
    this.status = status;
  }

  /** Whether trying again may help: network errors, rate limits and server errors. */
  get retryable(): boolean {
    return this.status === 0 || this.status === 408 || this.status === 429 || this.status >= 500;
  }
}

/** A JSON response: `body` is `null` for 404. */
export interface JsonResponse { status: number; body: unknown }

/** One request that resolves to parsed JSON, `null` on 404, and throws `HttpError` otherwise. */
export async function requestJson(fetchImpl: typeof fetch, url: string, init: RequestInit, timeoutMs: number): Promise<JsonResponse> {
  let res: Response;
  try {
    res = await fetchImpl(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
  } catch (err) {
    throw new HttpError(url, 0, err instanceof Error ? err.message : String(err));
  }
  const text = await res.text();
  if (res.status === 404) return { status: 404, body: null };
  if (!res.ok) throw new HttpError(url, res.status, text.slice(0, 200));
  try {
    return { status: res.status, body: JSON.parse(text) };
  } catch {
    throw new HttpError(url, res.status, 'invalid JSON');
  }
}
