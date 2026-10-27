import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Shader, rgbCss, rgbHex, type Face, type RGB } from '../src/index.ts';
import { style } from './fixtures.ts';

const luma = (c: RGB) => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];
const face = (mat: string, n: [number, number, number], o: Partial<Face> = {}): Face => ({ pts: [[0, 0, 0], [1, 0, 0], [0, 1, 0]], n, mat, tone: 0, depth: 0, ...o });

test('a ramp has one colour per step, dark to light, cooler in shadow and warmer in light', () => {
  const ramp = new Shader(style, 'n3').ramp('wall');
  assert.equal(ramp.length, style.steps);
  for (let i = 1; i < ramp.length; i++) assert.ok(luma(ramp[i]) > luma(ramp[i - 1]), `step ${i} is lighter`);
  const [dark, light] = [ramp[0], ramp[ramp.length - 1]];
  assert.ok(dark[2] / dark[0] > light[2] / light[0], 'shadow leans blue, light leans warm');
});

test('a face picks its step from its angle to the light plus its tone', () => {
  const shader = new Shader(style, 'n3'), ramp = shader.ramp('roof');
  const lit = shader.color(face('roof', [0, 0, 1])), away = shader.color(face('roof', [0.38, -0.52, -0.76]));
  assert.ok(luma(lit) > luma(away));
  assert.deepEqual(away, ramp[0]);
  assert.deepEqual(shader.color(face('roof', [0.38, -0.52, -0.76], { tone: 1 })), ramp[1]);
});

test('night tints, fade greys and emission lights a face; fill matches color', () => {
  const day = new Shader(style, 'n3'), night = new Shader(style, 'n3', { night: 1 }), faded = new Shader(style, 'n3', { fade: 1 });
  const wall = face('wall', [0, 0, 1]), glass = face('glass', [0, 0, 1], { emit: 1 });
  assert.ok(luma(night.color(wall)) < luma(day.color(wall)) * 0.7);
  const c = faded.color(face('roof', [0, 0, 1])), d = day.color(face('roof', [0, 0, 1]));
  assert.ok(Math.max(...c) - Math.min(...c) < Math.max(...d) - Math.min(...d), 'fade desaturates');
  assert.deepEqual(faded.color(face('roof', [0, 0, 1], { fade: 0 })), d, 'earned faces ignore the fade');
  assert.ok(luma(night.color(glass)) > luma(night.color(face('glass', [0, 0, 1]))), 'a lit window outshines an unlit one');
  for (const s of [day, night, faded]) for (const f of [wall, glass, face('roof', [0, 0, 1], { fade: 0.5 })]) {
    const rgb = rgbCss(s.color(f)).match(/\d+/g)!.map(Number);
    assert.equal(s.fill(f), `#${rgb.map(v => v.toString(16).padStart(2, '0')).join('')}`);
    assert.equal(s.fill(f), s.fill(f));
  }
});

test('hex colours clamp to the byte range like CSS rgb()', () => {
  assert.equal(rgbHex([300, -4, 15.9]), '#ff000f');
});
