import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Builder, box, paintLayout, paintOrder, type Anchor, type Face, type PaintGroup } from '../src/index.ts';

const face = (tag: string, depth: number): Face => ({ pts: [[0, 0, 0], [1, 0, 0], [0, 1, 0]], n: [0, 0, 1], mat: 'wall', tone: 0, depth, tag });
const group = (layer: PaintGroup['layer'], depth: number, faces: Face[], ordered = false): PaintGroup => ({ layer, depth, ordered, faces });
const tags = (faces: Face[]) => faces.map(f => f.tag).join(' ');

test('layers paint ground, then objects far to near, then top; ground keeps insertion order', () => {
  const groups = [
    group('top', -9, [face('top', 0)]),
    group('object', 5, [face('near', 0)]),
    group('ground', 9, [face('ground-a', 0)]),
    group('object', 1, [face('far', 0)]),
    group('ground', -9, [face('ground-b', 0)]),
  ];
  assert.equal(tags(paintOrder(groups)), 'ground-a ground-b far near top');
});

test('faces in a group sort far to near unless the group is ordered', () => {
  const faces = [face('c', 3), face('a', 1), face('b', 2)];
  assert.equal(tags(paintOrder([group('object', 0, faces)])), 'a b c');
  assert.equal(tags(paintOrder([group('object', 0, faces, true)])), 'c a b');
});

test('anchors paint right after the faces of their group and empty groups vanish', () => {
  const anchor: Anchor = { kind: 'glow', at: [0, 0, 0] };
  const groups: PaintGroup[] = [
    { ...group('object', 2, [face('front', 0)]) },
    { ...group('object', 1, [face('back-1', 0), face('back-2', 1)]), anchors: [anchor] },
    group('object', 0, []),
  ];
  const { faces, anchors } = paintLayout(groups);
  assert.equal(tags(faces), 'back-1 back-2 front');
  assert.equal(anchors[0].after, 2);
  assert.equal(anchor.after, undefined, 'the authored anchor is not mutated');
});

test('a stack sorts as one unit at its own depth, so a tall unit behind never covers one in front', () => {
  const b = new Builder({ seed: 1 });
  b.stack([0, 0, 0], () => b.object([0, 0, 30], () => box(b, [-1, -1, 0], [1, 1, 30], 'wall')));
  b.stack([3, 3, 0], () => b.object([3, 3, 0], () => box(b, [2, 2, 0], [4, 4, 1], 'roof')));
  const mats = b.build().faces.map(f => f.mat);
  assert.equal(mats.lastIndexOf('wall') < mats.indexOf('roof'), true);
});

test('an ordered object keeps the order its faces were added in', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => {
    b.face([[0, 0, 2], [1, 0, 2], [1, 1, 2]], 'wall', { tag: 'high' });
    b.face([[0, 0, 0], [1, 0, 0], [1, 1, 0]], 'wall', { tag: 'low' });
  }, { ordered: true });
  assert.equal(tags(b.build().faces), 'high low');
});
