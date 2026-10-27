import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Builder, box, centroidDepth, depthOf, avg, faceNormal, flat, norm, TransformStack, type Vec3 } from '../src/index.ts';

const close = (a: readonly number[], b: readonly number[], msg?: string) => a.forEach((v, i) => assert.ok(Math.abs(v - b[i]) < 1e-9, `${msg ?? ''} ${a} vs ${b}`));

test('a transform stack rotates then moves each child frame inside its parent', () => {
  const t = new TransformStack(Math.PI / 2, [10, 0, 1]);
  close(t.point([1, 0, 0]), [10, 1, 1], 'root');
  t.push([2, 0, 0], Math.PI / 2);
  close(t.point([0, 0, 0]), [10, 2, 1], 'child origin');
  close(t.point([1, 0, 2]), [9, 2, 3], 'child point');
  close(t.direction([1, 0, 0]), [-1, 0, 0], 'child direction');
  t.pop();
  close(t.point([1, 0, 0]), [10, 1, 1], 'back in root');
  t.pop();
  close(t.point([1, 0, 0]), [10, 1, 1], 'the root frame is never removed');
});

test('a builder frame places faces in world space and keeps their normals true', () => {
  const b = new Builder({ seed: 1 });
  b.push([5, 0, 0], Math.PI / 2);
  flat(b, [[0, 0], [1, 0], [1, 1]], 2, 'wall');
  b.pop();
  const [f] = b.build().faces;
  close(f.pts[1], [5, 1, 2]);
  close(f.n, [0, 0, 1]);
});

test('a box shows only the faces turned toward the camera', () => {
  const b = new Builder({ seed: 1 });
  let handles: ReturnType<typeof box> | undefined;
  b.object([0, 0, 0], () => { handles = box(b, [0, 0, 0], [1, 2, 3], 'wall'); });
  assert.ok(handles!.top >= 0 && handles!.xp >= 0 && handles!.yp >= 0);
  assert.equal(handles!.xn, -1);
  assert.equal(handles!.yn, -1);
  assert.equal(b.build().faces.length, 3);
});

test('a flat polygon faces up and a double-sided face survives facing away', () => {
  const b = new Builder({ seed: 1 });
  assert.ok(flat(b, [[0, 0], [1, 0], [0, 1]], 0, 'wall') >= 0);
  assert.equal(b.face([[0, 0, 0], [0, 0, 1], [1, -1, 1]], 'wall', { n: [-1, -1, 0] }), -1);
  assert.ok(b.face([[0, 0, 0], [0, 0, 1], [1, -1, 1]], 'wall', { n: [-1, -1, 0], double: true }) >= 0);
});

test('centroidDepth equals the depth of the averaged points and faceNormal is unit length', () => {
  const pts: Vec3[] = [[0.1, 2.3, 4.5], [1.7, -3.1, 0.2], [5.5, 0.3, -1.25], [2, 2, 2]];
  assert.equal(centroidDepth(pts), depthOf(avg(pts)));
  close(faceNormal([[0, 0, 0], [1, 0, 0], [0, 1, 0]]), [0, 0, 1]);
  close(faceNormal([[0, 0, 0], [2, 0, 0], [2, 0, 2]]), norm([0, -1, 0]));
});
