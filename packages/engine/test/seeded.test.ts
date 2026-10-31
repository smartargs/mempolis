import assert from 'node:assert/strict';
import { test } from 'node:test';
import { box, Builder, mulberry32 } from '../src/index.ts';

const cube = (b: Builder, x: number) => box(b, [x, 0, 0], [x + 1, 1, b.rng() + 1], 'wall');

test('a seeded sub-tree builds the same faces whatever was built before it', () => {
  const plain = new Builder({ seed: 1 }), busy = new Builder({ seed: 99 });
  for (let i = 0; i < 7; i++) busy.rng();
  plain.seeded(42, () => cube(plain, 0));
  busy.seeded(42, () => cube(busy, 0));
  assert.deepEqual(plain.build().faces, busy.build().faces);
});

test('the outer random source carries on after a seeded sub-tree as if it had not run', () => {
  const b = new Builder({ seed: 5 }), reference = mulberry32(5);
  b.rng();
  reference();
  const inner = b.seeded(9, () => [b.rng(), b.rng()]), fresh = mulberry32(9);
  assert.deepEqual(inner, [fresh(), fresh()]);
  assert.equal(b.rng(), reference());
});

test('the outer random source comes back even when the sub-tree throws', () => {
  const b = new Builder({ seed: 3 }), outer = b.rng;
  assert.throws(() => b.seeded(4, () => { throw new Error('boom'); }));
  assert.equal(b.rng, outer);
});
