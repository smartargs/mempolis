import { mock } from 'node:test';

/** One request a fake fetch received, with the mocked clock reading when it arrived. */
export interface FakeCall { url: string; method: string; body?: string; at: number }

/** What a fake route answers: a JSON body, a status with an optional raw body, or a thrown network error. */
export type FakeReply = { json: unknown; status?: number } | { status: number; text?: string } | { error: string };

/** A `fetch` that answers from `route` and records every call. */
export function fakeFetch(route: (url: string, call: FakeCall) => FakeReply | Promise<FakeReply>) {
  const calls: FakeCall[] = [];
  const fetchImpl = (async (input: string | URL | Request, init?: RequestInit) => {
    const call: FakeCall = { url: String(input), method: init?.method ?? 'GET', body: init?.body as string | undefined, at: Date.now() };
    calls.push(call);
    const reply = await route(call.url, call);
    if ('error' in reply) throw new TypeError(reply.error);
    if ('json' in reply) return new Response(JSON.stringify(reply.json), { status: reply.status ?? 200 });
    return new Response(reply.text ?? '', { status: reply.status });
  }) as typeof fetch;
  return { fetch: fetchImpl, calls };
}

/** Lets pending promise callbacks and real immediates run. */
export async function flush(rounds = 20) {
  for (let i = 0; i < rounds; i++) await new Promise<void>(resolve => setImmediate(resolve));
}

/** Steps mocked time by `stepMs` until `promise` settles, so each timer sees the clock it was due at. */
export async function settle<T>(promise: Promise<T>, stepMs = 10): Promise<{ value?: T; error?: unknown }> {
  let done = false;
  const outcome = promise.then(value => ({ value }), (error: unknown) => ({ error })).finally(() => { done = true; });
  for (let elapsed = 0; !done; elapsed += stepMs) {
    await flush(5);
    if (done) break;
    if (elapsed > 600_000) throw new Error('promise did not settle within ten fake minutes');
    mock.timers.tick(stepMs);
  }
  return outcome;
}

/** Fakes `setTimeout` and `Date` from time 0 for one test. */
export function useFakeTimers() {
  mock.timers.enable({ apis: ['setTimeout', 'Date'], now: 0 });
}

/** A promise with its resolver exposed. */
export function deferred<T = void>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(r => { resolve = r; });
  return { promise, resolve };
}
