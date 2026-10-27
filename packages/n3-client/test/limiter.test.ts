import assert from 'node:assert/strict';
import { afterEach, beforeEach, mock, test } from 'node:test';
import { Limiter } from '../src/limiter.ts';
import { deferred, flush, settle, useFakeTimers } from './fake-fetch.ts';

beforeEach(useFakeTimers);
afterEach(() => mock.timers.reset());

test('queued tasks run in the order they were submitted', async () => {
  const l = new Limiter(1, 0), order: number[] = [];
  await settle(Promise.all([1, 2, 3, 4].map(i => l.run(async () => { order.push(i); }))));
  assert.deepEqual(order, [1, 2, 3, 4]);
});

test('a failing task frees its slot and rejects only its own caller', async () => {
  const l = new Limiter(1, 0);
  const failing = l.run(async () => { throw new Error('boom'); });
  const next = l.run(async () => 'next');
  assert.equal((await settle(failing)).error instanceof Error, true);
  assert.equal((await settle(next)).value, 'next');
});

test('spacing is reserved per start, so a burst is spread out from the first start', async () => {
  const l = new Limiter(10, 50), starts: number[] = [];
  await settle(Promise.all([0, 1, 2].map(() => l.run(async () => { starts.push(Date.now()); }))));
  assert.deepEqual(starts, [0, 50, 100]);
  mock.timers.tick(500);
  await settle(l.run(async () => { starts.push(Date.now()); }));
  assert.equal(starts[3], 600);
});

test('a task submitted in the same microtask turn as a release does not overshoot the concurrency limit', async () => {
  const l = new Limiter(1, 0), a = deferred<string>(), never = new Promise<string>(() => {});
  let started = 0;
  const first = l.run(() => { started++; return a.promise; });
  void l.run(() => { started++; return never; });
  await flush();
  a.promise.then(() => { void l.run(() => { started++; return never; }); });
  a.resolve('a');
  await first;
  await flush();
  assert.equal(started - 1, 1, 'tasks in flight after the first finished');
});
