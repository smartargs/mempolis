import assert from 'node:assert/strict';
import { test } from 'node:test';
import { filterDisplayList, type Anchor, type DisplayList, type Face } from '../src/index.ts';

const face = (part: number): Face => ({ pts: [[0, 0, 0], [1, 0, 0], [0, 1, 0]], n: [0, 0, 1], mat: 'wall', tone: 0, depth: 0, part });
const anchor = (part: number, after?: number): Anchor => ({ kind: 'glow', at: [0, 0, 0], part, after });

test('filtering keeps paint order and moves each kept anchor to count only the kept faces', () => {
  const dl: DisplayList = {
    faces: [face(1), face(2), face(1), face(2), face(1)],
    anchors: [anchor(1, 0), anchor(1, 2), anchor(2, 3), anchor(1, 4), anchor(1, 5), anchor(1, 99), anchor(1)],
  };
  const out = filterDisplayList(dl, f => f.part === 1, a => a.part === 1);
  assert.equal(out.faces.length, 3);
  assert.ok(out.faces.every(f => f.part === 1));
  assert.deepEqual(out.anchors.map(a => a.after), [0, 1, 2, 3, 3, undefined]);
  assert.equal(dl.anchors[1].after, 2, 'the source list is not changed');
});

test('an anchor placed before a dropped face keeps its place before the next kept face', () => {
  const dl: DisplayList = { faces: [face(2), face(2), face(1)], anchors: [anchor(1, 1)] };
  const out = filterDisplayList(dl, f => f.part === 1, () => true);
  assert.equal(out.anchors[0].after, 0);
});
