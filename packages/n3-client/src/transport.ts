import type { JsonCache } from './cache.ts';
import { requestJson } from './http.ts';
import { Limiter } from './limiter.ts';
import { withRetry } from './retry.ts';

/** Options shared by every request a client makes. */
export interface TransportOptions {
  /** Where responses are kept; `null` disables caching. */
  cache?: JsonCache | null;
  /** Requests in flight at once (default 4). */
  concurrency?: number;
  /** Minimum gap between request starts in milliseconds (default 120). */
  spacingMs?: number;
  /** Retries per request (default 4). */
  retries?: number;
  /** First backoff in milliseconds, doubled per retry (default 800). */
  backoffMs?: number;
  /** Per-request timeout in milliseconds (default 30000). */
  timeoutMs?: number;
  fetch?: typeof fetch;
}

/** Counters for one transport. */
export interface TransportStats { requests: number; cacheHits: number; retries: number; failures: number }

/** Sends JSON requests through a cache, a concurrency limit and a retry policy. */
export class Transport {
  readonly stats: TransportStats = { requests: 0, cacheHits: 0, retries: 0, failures: 0 };
  private readonly cache: JsonCache | null;
  private readonly limiter: Limiter;
  private readonly opts: Required<Omit<TransportOptions, 'cache'>>;

  constructor(o: TransportOptions = {}) {
    this.cache = o.cache ?? null;
    this.opts = {
      concurrency: o.concurrency ?? 4,
      spacingMs: o.spacingMs ?? 120,
      retries: o.retries ?? 4,
      backoffMs: o.backoffMs ?? 800,
      timeoutMs: o.timeoutMs ?? 30000,
      fetch: o.fetch ?? fetch,
    };
    this.limiter = new Limiter(this.opts.concurrency, this.opts.spacingMs);
  }

  /** GET `url` as JSON; `null` on 404. Responses are cached unless `cache` is false. */
  get<T>(url: string, o: { cache?: boolean } = {}): Promise<T | null> {
    return this.send<T>(`GET ${url}`, url, { method: 'GET' }, o.cache ?? true);
  }

  /** POST `body` as JSON to `url`; `null` on 404. Responses are cached unless `cache` is false. */
  post<T>(url: string, body: unknown, o: { cache?: boolean } = {}): Promise<T | null> {
    const json = JSON.stringify(body);
    return this.send<T>(`POST ${url} ${json}`, url, { method: 'POST', body: json, headers: { 'content-type': 'application/json' } }, o.cache ?? true);
  }

  private async send<T>(key: string, url: string, init: RequestInit, useCache: boolean): Promise<T | null> {
    const hit = useCache ? this.cache?.get(key) : undefined;
    if (hit) { this.stats.cacheHits++; return hit.value as T | null; }
    try {
      const res = await withRetry(() => this.limiter.run(() => { this.stats.requests++; return requestJson(this.opts.fetch, url, init, this.opts.timeoutMs); }),
        { retries: this.opts.retries, backoffMs: this.opts.backoffMs }, () => { this.stats.retries++; });
      if (useCache) this.cache?.set(key, res.body);
      return res.body as T | null;
    } catch (err) {
      this.stats.failures++;
      throw err;
    }
  }
}
