import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Builder, type Vec3 } from '../src/index.ts';

const quad = (x: number, y: number, z: number, s = 1): Vec3[] => [[x, y, z], [x + s, y, z], [x + s, y + s, z], [x, y + s, z]];

test('decals on one surface paint in the order they were added, however many there are', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => {
    const wall = b.face(quad(0, 0, 0, 10), 'wall', { n: [0, 0, 1], tag: 'wall' });
    for (let i = 0; i < 1200; i++) b.face(quad(i * 0.001, 0, 0.001), 'roof', { n: [0, 0, 1], decalOf: wall, tag: `d${i}` });
  });
  const tags = b.build().faces.map(f => f.tag);
  assert.deepEqual(tags, ['wall', ...Array.from({ length: 1200 }, (_, i) => `d${i}`)]);
});

test('a decal stays on its surface and never paints over a face that is nearer than that surface', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => {
    const wall = b.face(quad(0, 0, 0), 'wall', { n: [0, 0, 1], tag: 'wall' });
    b.face(quad(0, 0, 0.0002), 'roof', { n: [0, 0, 1], tag: 'nearer' });
    for (let i = 0; i < 40; i++) b.face(quad(0.2, 0.2, 0.001, 0.2), 'roof', { n: [0, 0, 1], decalOf: wall, tag: 'decal' });
  });
  const tags = b.build().faces.map(f => f.tag);
  assert.equal(tags.indexOf('wall'), 0);
  assert.equal(tags.lastIndexOf('decal') < tags.indexOf('nearer'), true);
});

test('a decal on a decal paints after it and keeps insertion order with the other decals of that surface', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => {
    const wall = b.face(quad(0, 0, 0, 4), 'wall', { n: [0, 0, 1], tag: 'wall' });
    const sign = b.face(quad(0, 0, 0.01), 'roof', { n: [0, 0, 1], decalOf: wall, tag: 'sign' });
    b.face(quad(1, 1, 0.01), 'roof', { n: [0, 0, 1], decalOf: wall, tag: 'window' });
    b.face(quad(0, 0, 0.02, 0.5), 'roof', { n: [0, 0, 1], decalOf: sign, tag: 'tag-on-sign' });
  });
  assert.deepEqual(b.build().faces.map(f => f.tag), ['wall', 'sign', 'window', 'tag-on-sign']);
});
