import assert from 'node:assert/strict';
import { afterEach, beforeEach, mock, test } from 'node:test';
import { HttpError, memoryCache, Transport, type TransportOptions } from '../src/index.ts';
import { deferred, fakeFetch, flush, settle, useFakeTimers, type FakeReply } from './fake-fetch.ts';

const URL_A = 'https://api.test/a';

beforeEach(useFakeTimers);
afterEach(() => mock.timers.reset());

function scripted(replies: FakeReply[]) {
  let i = 0;
  return fakeFetch(() => replies[Math.min(i++, replies.length - 1)]);
}

const transport = (f: { fetch: typeof fetch }, o: TransportOptions = {}) => new Transport({ spacingMs: 0, ...o, fetch: f.fetch });

test('retryable failures are retried with exponential backoff until one succeeds', async () => {
  const f = scripted([{ status: 503 }, { status: 500 }, { status: 429 }, { json: { ok: 1 } }]);
  const t = transport(f, { backoffMs: 800, retries: 4 });
  const { value } = await settle(t.get(URL_A));
  assert.deepEqual(value, { ok: 1 });
  assert.deepEqual(f.calls.map(c => c.at), [0, 800, 2400, 5600]);
  assert.deepEqual(t.stats, { requests: 4, cacheHits: 0, retries: 3, failures: 0 });
});

test('network errors and 408 timeouts are retryable', async () => {
  const f = scripted([{ error: 'connection reset' }, { status: 408 }, { json: 7 }]);
  const t = transport(f, { backoffMs: 10 });
  assert.equal((await settle(t.get(URL_A))).value, 7);
  assert.equal(t.stats.retries, 2);
});

test('after the last retry the error is thrown and counted once as a failure', async () => {
  const f = scripted([{ status: 502 }]);
  const t = transport(f, { retries: 2, backoffMs: 100 });
  const { error } = await settle(t.get(URL_A));
  assert.ok(error instanceof HttpError);
  assert.equal(error.status, 502);
  assert.equal(error.retryable, true);
  assert.equal(f.calls.length, 3);
  assert.deepEqual(t.stats, { requests: 3, cacheHits: 0, retries: 2, failures: 1 });
});

test('client errors are thrown at once without retrying', async () => {
  for (const status of [400, 401, 403, 422]) {
    const f = scripted([{ status, text: 'nope' }]);
    const t = transport(f);
    const { error } = await settle(t.get(URL_A));
    assert.ok(error instanceof HttpError, String(status));
    assert.equal(error.status, status);
    assert.equal(error.retryable, false);
    assert.match(error.message, /nope/);
    assert.equal(f.calls.length, 1, String(status));
  }
});

test('a body that is not JSON fails without retrying', async () => {
  const f = scripted([{ status: 200, text: '<html>' }]);
  const t = transport(f);
  const { error } = await settle(t.get(URL_A));
  assert.ok(error instanceof HttpError);
  assert.match(error.message, /invalid JSON/);
  assert.equal(f.calls.length, 1);
});

test('404 resolves to null without retrying', async () => {
  const f = scripted([{ status: 404, text: 'not found' }]);
  const t = transport(f);
  assert.equal((await settle(t.get(URL_A))).value, null);
  assert.equal(f.calls.length, 1);
});

test('retries: 0 makes exactly one attempt', async () => {
  const f = scripted([{ status: 500 }]);
  const t = transport(f, { retries: 0 });
  assert.ok((await settle(t.get(URL_A))).error instanceof HttpError);
  assert.equal(f.calls.length, 1);
});

test('no more than `concurrency` requests are in flight at once', async () => {
  const gates = Array.from({ length: 6 }, () => deferred());
  let inFlight = 0, peak = 0, n = 0;
  const f = fakeFetch(async () => {
    const gate = gates[n++];
    inFlight++;
    peak = Math.max(peak, inFlight);
    await gate.promise;
    inFlight--;
    return { json: null };
  });
  const t = transport(f, { concurrency: 2 });
  const all = Promise.all(gates.map((_, i) => t.get(`${URL_A}/${i}`)));
  await flush();
  assert.equal(f.calls.length, 2);
  gates[0].resolve();
  await flush();
  assert.equal(f.calls.length, 3);
  for (const g of gates) g.resolve();
  await settle(all);
  assert.equal(f.calls.length, 6);
  assert.equal(peak, 2);
});

test('request starts are spaced by at least `spacingMs`, also when they run in parallel', async () => {
  const f = scripted([{ json: 1 }]);
  const t = transport(f, { spacingMs: 120, concurrency: 8 });
  await settle(Promise.all([0, 1, 2, 3].map(i => t.get(`${URL_A}/${i}`))));
  assert.deepEqual(f.calls.map(c => c.at), [0, 120, 240, 360]);
});

test('backoff waits do not hold a concurrency slot', async () => {
  let first = true;
  const f = fakeFetch(url => {
    if (url.endsWith('/flaky') && first) { first = false; return { status: 503 }; }
    return { json: url };
  });
  const t = transport(f, { concurrency: 1, backoffMs: 1000 });
  const flaky = t.get(`${URL_A}/flaky`), steady = t.get(`${URL_A}/steady`);
  await settle(Promise.all([flaky, steady]));
  const steadyCall = f.calls.find(c => c.url.endsWith('/steady'))!;
  assert.equal(steadyCall.at, 0);
  assert.deepEqual(f.calls.filter(c => c.url.endsWith('/flaky')).map(c => c.at), [0, 1000]);
});

test('responses are cached by method, URL and body, including null for 404', async () => {
  const f = fakeFetch(url => (url.endsWith('/missing') ? { status: 404 } : { json: { url } }));
  const t = transport(f, { cache: memoryCache() });
  await settle(t.get(URL_A));
  assert.deepEqual((await settle(t.get(URL_A))).value, { url: URL_A });
  assert.equal((await settle(t.get(`${URL_A}/missing`))).value, null);
  assert.equal((await settle(t.get(`${URL_A}/missing`))).value, null);
  await settle(t.post(URL_A, { id: 1 }));
  await settle(t.post(URL_A, { id: 1 }));
  await settle(t.post(URL_A, { id: 2 }));
  assert.deepEqual(f.calls.map(c => `${c.method} ${c.body ?? ''}`), ['GET ', 'GET ', 'POST {"id":1}', 'POST {"id":2}']);
  assert.equal(t.stats.cacheHits, 3);
});

test('`cache: false` neither reads nor writes the cache', async () => {
  const cache = memoryCache();
  cache.set(`GET ${URL_A}`, 'stale');
  const f = scripted([{ json: 'fresh' }]);
  const t = transport(f, { cache });
  assert.equal((await settle(t.get(URL_A, { cache: false }))).value, 'fresh');
  assert.equal(cache.get(`GET ${URL_A}`)?.value, 'stale');
  assert.equal((await settle(t.get(URL_A))).value, 'stale');
});

test('failed requests are not cached', async () => {
  const cache = memoryCache();
  const f = scripted([{ status: 400 }, { json: 'ok' }]);
  const t = transport(f, { cache });
  assert.ok((await settle(t.get(URL_A))).error);
  assert.equal(cache.get(`GET ${URL_A}`), undefined);
  assert.equal((await settle(t.get(URL_A))).value, 'ok');
});

test('POST sends JSON with a content type', async () => {
  let init: RequestInit | undefined;
  const t = new Transport({ spacingMs: 0, fetch: (async (_u: string, i: RequestInit) => { init = i; return new Response('{}'); }) as typeof fetch });
  await settle(t.post(URL_A, { a: [1, 2] }));
  assert.equal(init?.method, 'POST');
  assert.equal(init?.body, '{"a":[1,2]}');
  assert.deepEqual(init?.headers, { 'content-type': 'application/json' });
  assert.ok(init?.signal instanceof AbortSignal);
});
