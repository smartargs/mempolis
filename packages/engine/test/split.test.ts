import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Builder, box, flat, type Face } from '../src/index.ts';

const widthOf = (faces: Face[], axis: 0 | 1) => {
  const vs = faces.flatMap(f => f.pts.map(p => p[axis]));
  return Math.max(...vs) - Math.min(...vs);
};

const areaOf = (f: Face) => {
  let x = 0, y = 0, z = 0;
  for (let i = 0; i < f.pts.length; i++) {
    const a = f.pts[i], c = f.pts[(i + 1) % f.pts.length];
    x += a[1] * c[2] - a[2] * c[1]; y += a[2] * c[0] - a[0] * c[2]; z += a[0] * c[1] - a[1] * c[0];
  }
  return Math.hypot(x, y, z) / 2;
};

function kerbBeside(split?: number) {
  const b = new Builder({ seed: 1 });
  b.object([10, 0, 0], () => box(b, [0, -0.1, 0], [20, 0.1, 0.1], 'wall'), { split });
  b.object([2.5, 1.5, 0], () => box(b, [2, 1, 0], [3, 2, 3], 'roof'));
  return b.build().faces;
}

test('a long group split into short pieces paints its far end before a nearer object beside it', () => {
  const whole = kerbBeside(), cut = kerbBeside(3);
  const lastFarKerb = (faces: Face[]) => Math.max(...faces.flatMap((f, i) => f.mat === 'wall' && Math.min(...f.pts.map(p => p[0])) < 2.5 ? [i] : []));
  const firstPost = (faces: Face[]) => faces.findIndex(f => f.mat === 'roof');
  assert.equal(lastFarKerb(whole) > firstPost(whole), true, 'unsplit, the kerb paints over the nearer post');
  assert.equal(lastFarKerb(cut) < firstPost(cut), true, 'split, the kerb end behind the post paints first');
});

test('a split group keeps its whole surface, cut into pieces no wider than the span', () => {
  const b = new Builder({ seed: 1 });
  b.object([10, 0, 0], () => box(b, [0, -0.1, 0], [20, 0.1, 0.1], 'wall'), { split: 3 });
  for (const f of b.build().faces) assert.ok(widthOf([f], 0) <= 3 + 1e-9);
  const whole = new Builder({ seed: 1 });
  whole.object([10, 0, 0], () => box(whole, [0, -0.1, 0], [20, 0.1, 0.1], 'wall'));
  const sum = (faces: Face[]) => faces.reduce((s, f) => s + areaOf(f), 0);
  assert.ok(Math.abs(sum(b.build().faces) - sum(whole.build().faces)) < 1e-9);
});

test('a decal across a cut stays whole and paints after every piece of the surface under it', () => {
  const b = new Builder({ seed: 1 });
  b.object([10, 0, 0], () => {
    const top = box(b, [0, -0.1, 0], [20, 0.1, 0.1], 'wall').top;
    flat(b, [[14, -0.1], [16, -0.1], [16, 0.1], [14, 0.1]], 0.106, 'roof', { decalOf: top, tag: 'decal' });
  }, { split: 3 });
  const faces = b.build().faces, d = faces.findIndex(f => f.tag === 'decal');
  const xs = (f: Face) => f.pts.map(p => p[0]);
  const under = faces.flatMap((f, i) => f.n[2] > 0.9 && f.mat === 'wall' && Math.max(...xs(f)) > 14 && Math.min(...xs(f)) < 16 ? [i] : []);
  assert.equal(faces.filter(f => f.tag === 'decal').length, 1, 'the decal is not cut');
  assert.equal(under.length, 2, 'the surface under it is cut in two');
  assert.ok(under.every(i => i < d), 'both pieces paint before the decal');
});

test('decals stacked on one spot keep their order even when only the larger one crosses a cut', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => {
    const deck = flat(b, [[-4, -4], [4, -4], [4, 4], [-4, 4]], 0, 'wall');
    flat(b, [[1, 1], [5, 1], [5, 5], [1, 5]], 0.005, 'roof', { decalOf: deck, tag: 'ring' });
    flat(b, [[1.5, 1.5], [2.5, 1.5], [2.5, 2.5], [1.5, 2.5]], 0.01, 'wall', { decalOf: deck, tag: 'core' });
  }, { ordered: true, split: 3 });
  const tags = b.build().faces.map(f => f.tag);
  assert.ok(tags.indexOf('ring') < tags.indexOf('core'));
});

test('in an ordered group a short strip laid on an earlier surface stays whole and paints after the surface under it', () => {
  const b = new Builder({ seed: 1 });
  b.object([10, 0, 1], () => {
    b.face([[0, 0, 0], [20, 0, 0], [20, 0, 2], [0, 0, 2]], 'wall', { n: [0, 1, 0], tag: 'wall' });
    b.face([[8, 0.05, 1], [13, 0.05, 1], [13, 0.05, 1.2], [8, 0.05, 1.2]], 'roof', { n: [0, 1, 0], tag: 'strip' });
  }, { ordered: true, split: 3 });
  const faces = b.build().faces, strip = faces.findIndex(f => f.tag === 'strip');
  const under = faces.flatMap((f, i) => f.tag === 'wall' && Math.max(...f.pts.map(p => p[0])) > 8 && Math.min(...f.pts.map(p => p[0])) < 13 ? [i] : []);
  assert.equal(faces.filter(f => f.tag === 'strip').length, 1);
  assert.equal(under.length, 3);
  assert.ok(under.every(i => i < strip));
});

test('a lit face cut into pieces still casts one night halo', () => {
  const b = new Builder({ seed: 1 });
  b.object([10, 0, 0], () => b.face([[0, 0, 1], [20, 0, 1], [20, 0, 1.2], [0, 0, 1.2]], 'wall', { n: [0, 1, 0], emit: 0.9 }), { split: 3 });
  const lit = b.build().faces.filter(f => f.emit);
  assert.ok(lit.length > 1);
  assert.equal(lit.filter(f => f.halo !== false).length, 1);
});

test('narrow groups, ground groups and logos are never cut', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => box(b, [0, 0, 0], [2, 2, 2], 'wall'), { split: 3 });
  b.object([0, 0, -5], () => flat(b, [[0, 0], [20, 0], [20, 20], [0, 20]], 0, 'wall'), { layer: 'ground', split: 3 });
  b.object([0, 0, 0], () => b.logo([[0, 0, 2], [10, 0, 2], [10, 0, 0], [0, 0, 0]], 'mark'), { split: 3 });
  const faces = b.build().faces;
  assert.equal(faces.length, 3 + 1 + 1);
  assert.equal(faces.filter(f => f.logo).length, 1);
});

test('splitting is deterministic and independent of what else the builder holds', () => {
  const build = (before: boolean) => {
    const b = new Builder({ seed: 7 });
    if (before) b.object([50, 50, 0], () => box(b, [49, 49, 0], [51, 51, 4], 'roof'));
    b.object([10, 5, 0], () => { box(b, [0, 0, 0], [20, 10, 3], 'wall'); b.anchor('glow', [18, 9, 3]); }, { split: 3 });
    return b.build();
  };
  const own = (dl: ReturnType<typeof build>) => JSON.stringify({ faces: dl.faces.filter(f => f.mat === 'wall').map(f => ({ ...f, tone: 0 })), anchors: dl.anchors.map(a => a.at) });
  assert.equal(own(build(false)), own(build(true)));
});
