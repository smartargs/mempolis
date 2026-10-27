import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Builder, box, frameFit, type Canvas2D, type Path2DCtor, renderCanvas, renderFrame, renderSVG, type CanvasOpts, type DisplayList, type Face, type Vec3 } from '../src/index.ts';
import { style } from './fixtures.ts';
import { RecordingCanvas, RecordingPath } from './recording-canvas.ts';

const draw = (dl: DisplayList, o: CanvasOpts = {}) => { const ctx = new RecordingCanvas(); renderCanvas(ctx, dl, style, 'n3', o); return ctx; };
const quad = (x: number, z: number): Vec3[] => [[x, 0, z + 1], [x + 1, -1, z + 1], [x + 1, -1, z], [x, 0, z]];
const kinds = (ctx: RecordingCanvas) => ctx.fills().map(f => (f.globalCompositeOperation === 'screen' ? 'glow' : f.path ? 'logo' : String(f.fillStyle)));

function scene(): DisplayList {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => b.anchor('glow', [0, 0, 1], { r: 1 }));
  b.object([3, 3, 0], () => box(b, [2, 2, 0], [4, 4, 3], 'wall'));
  b.object([6, 6, 0], () => b.face(quad(6, 0), 'glass', { emit: 1, n: [1, 1, 0] }));
  return b.build();
}

test('renderSVG and renderCanvas share one frame', () => {
  const dl = scene();
  for (const o of [{}, { px: 7.3, pad: 0.25, headroom: 0 }, { px: 31, bounds: { x0: -2, y0: -3, x1: 9.37, y1: 4.11 } }]) {
    const frame = renderFrame(dl, o), svg = renderSVG(dl, style, 'n3', { ...o, size: true });
    assert.match(svg, new RegExp(`viewBox="${frame.x} ${frame.y} ${frame.w} ${frame.h}" width="${frame.width}" height="${frame.height}"`));
    const ctx = new RecordingCanvas();
    assert.deepEqual(renderCanvas(ctx, dl, style, 'n3', o), frame);
    const fit = frameFit(frame);
    assert.deepEqual(ctx.calls.find(c => c[0] === 'transform'), ['transform', fit.s, 0, 0, fit.s, fit.tx, fit.ty]);
  }
});

test('a frame fits a box the way an SVG viewBox fits its image: uniform scale, centred', () => {
  const frame = { px: 10, x: -50, y: -20, w: 100, h: 40, width: 100, height: 40 };
  assert.deepEqual(frameFit(frame), { s: 1, tx: 50, ty: 20 });
  assert.deepEqual(frameFit(frame, 200, 200), { s: 2, tx: 100, ty: 100 });
});

test('faces trace the same pixel points renderSVG writes and are filled and stroked in their colour', () => {
  const dl = scene(), ctx = draw(dl), svg = renderSVG(dl, style, 'n3');
  const first = /<polygon points="([^"]+)" fill="([^"]+)"/.exec(svg)!;
  const pts = ctx.calls.slice(ctx.calls.findIndex(c => c[0] === 'moveTo')).filter(c => c[0] === 'moveTo' || c[0] === 'lineTo').slice(0, first[1].split(' ').length);
  assert.equal(pts.map(c => `${c[1]},${c[2]}`).join(' '), first[1]);
  const [fill] = ctx.fills(), stroke = ctx.calls.find(c => c[0] === 'stroke')![1] as { strokeStyle: string; lineWidth: number; lineJoin: string };
  assert.equal(fill.fillStyle, first[2]);
  assert.deepEqual([stroke.strokeStyle, stroke.lineWidth, stroke.lineJoin], [first[2], 0.6, 'round']);
  assert.equal(ctx.depth(), 0, 'every save is restored');
});

test('night glows are screen-blended where renderSVG puts them: anchors before the face they precede, halos right after their face', () => {
  const dl = scene();
  assert.equal(dl.anchors[0].after, 0);
  const night = draw(dl, { night: 1 }), order = kinds(night);
  assert.equal(order[0], 'glow', 'the light behind the wall paints before the wall');
  assert.equal(order.at(-1), 'glow', 'the lit window halo follows the window');
  assert.equal(order.filter(k => k === 'glow').length, 2);
  const svg = renderSVG(dl, style, 'n3', { night: 1 });
  assert.equal(svg.match(/<ellipse/g)!.length, 2);
  const halo = night.fills()[0];
  assert.ok(Math.abs(halo.globalAlpha - 0.9) < 1e-9);
  const stops = night.calls.find(c => c[0] === 'gradient')!.at(-1) as [number, string][];
  assert.deepEqual(stops.map(s => s[0]), [0, 1]);
  assert.match(stops[0][1], /^rgba\(255,214,120,0\.85\)$/);
  assert.match(stops[1][1], /,0\)$/);
});

test('no glow by day, below the night threshold, or with glow turned off', () => {
  const dl = scene();
  for (const o of [{ night: 0 }, { night: 0.05 }, { night: 1, glow: 0 }]) assert.ok(!kinds(draw(dl, o)).includes('glow'), JSON.stringify(o));
  const weak = draw(dl, { night: 1, glow: 0.5 }).fills().filter(f => f.globalCompositeOperation === 'screen');
  assert.equal(weak.length, 2);
  assert.ok(Math.abs(weak[1].globalAlpha - 0.43) < 1e-9, 'face halos scale with glow strength');
});

test('glows anchored past the last face paint after every face, before smoke', () => {
  const dl = scene();
  const loose: DisplayList = { faces: dl.faces, anchors: [{ kind: 'smoke', at: [0, 0, 4], data: { puffs: 3 } }, { kind: 'glow', at: [9, 9, 0] }] };
  const ctx = draw(loose, { night: 1 }), fills = ctx.fills();
  const glows = fills.flatMap((f, i) => (f.globalCompositeOperation === 'screen' ? [i] : []));
  assert.equal(glows.at(-1), fills.length - 7, 'the loose glow comes after the window halo and before three two-part puffs');
  const puffs = fills.slice(-6);
  assert.deepEqual(puffs.map(f => f.globalAlpha), [0.85, 0.85, 0.67, 0.67, 0.49, 0.49]);
  assert.deepEqual(puffs.map(f => f.rule), ['evenodd', undefined, 'evenodd', undefined, 'evenodd', undefined]);
});

test('face alpha becomes the global alpha of its fill and stroke only', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => { b.face(quad(0, 0), 'wall', { n: [1, 1, 0], alpha: 0.4 }); b.face(quad(2, 0), 'wall', { n: [1, 1, 0] }); }, { ordered: true });
  const ctx = draw(b.build());
  assert.deepEqual(ctx.fills().map(f => f.globalAlpha), [0.4, 1]);
  assert.deepEqual(ctx.calls.filter(c => c[0] === 'stroke').map(c => (c[1] as { globalAlpha: number }).globalAlpha), [0.4, 1]);
});

function logoScene(id: string, emit?: number): DisplayList {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => b.logo([[0, 2, 2], [2, 0, 2], [2, 0, 1], [0, 2, 1]], id, { emit }));
  return b.build();
}

test('a logo draws its shapes across its quad with the same matrix as renderSVG, dimmed at night unless lit', () => {
  const dl = logoScene('mark'), svg = renderSVG(dl, style, 'n3', { night: 1 });
  const matrix = /matrix\(([^)]+)\)/.exec(svg)![1].split(' ').map(Number);
  const ctx = draw(dl, { night: 1, path2d: RecordingPath });
  const t = ctx.calls.filter(c => c[0] === 'transform')[1].slice(1) as number[];
  t.forEach((v, i) => assert.ok(Math.abs(v - matrix[i]) < 1e-4));
  const fills = ctx.fills(), stroke = ctx.calls.find(c => c[0] === 'stroke')![1] as { path: RecordingPath; lineWidth: number; lineCap: string; strokeStyle: string };
  assert.equal(fills.length, 3);
  assert.match((fills[0].path as RecordingPath).d, /^M1 0h8a1 1 0 0 1 1 1v3/);
  assert.deepEqual([fills[0].fillStyle, fills[0].globalAlpha], ['#00e599', 1]);
  assert.deepEqual([(fills[1].path as RecordingPath).d, fills[1].rule, fills[1].globalAlpha, fills[1].fillStyle], ['M1 1h2v2z', 'evenodd', 0.5, '#000']);
  assert.deepEqual([stroke.strokeStyle, stroke.lineWidth, stroke.lineCap], ['#fff', 0.5, 'round']);
  assert.match(stroke.path.d, /^M4 2a1 1 0 1 0 2 0/);
  assert.deepEqual([fills[2].fillStyle, fills[2].globalAlpha], ['rgb(6,8,16)', 0.5]);
  assert.equal(draw(dl, { night: 0, path2d: RecordingPath }).fills().length, 2, 'no dimming by day');
  assert.equal(ctx.depth(), 0);
});

test('logo paths are parsed once and reused', () => {
  const dl = logoScene('mark');
  const a = draw(dl, { path2d: RecordingPath }).fills()[0].path, b = draw(dl, { path2d: RecordingPath }).fills()[0].path;
  assert.ok(a instanceof RecordingPath);
  assert.equal(a, b);
});

test('a lit logo with a glow colour casts a halo; without Path2D logos are skipped quietly', () => {
  const lit = draw(logoScene('lit', 1), { night: 1, path2d: RecordingPath });
  assert.deepEqual(kinds(lit), ['logo', 'glow']);
  assert.equal(kinds(draw(logoScene('mark', 1), { night: 1, path2d: RecordingPath })).includes('glow'), false, 'a logo without glow casts none');
  assert.equal((globalThis as { Path2D?: unknown }).Path2D, undefined);
  const bare = draw(logoScene('lit', 1), { night: 1 });
  assert.deepEqual(kinds(bare), ['glow']);
});

test('a face whose logo is missing from the style draws nothing', () => {
  const dl = logoScene('nope', 1);
  assert.equal(draw(dl, { night: 1, path2d: RecordingPath }).fills().length, 0);
  const face: Face = dl.faces[0];
  assert.equal(face.logo, 'nope');
});

test('browser and worker canvas contexts and Path2D fit the structural types', () => {
  const asContext = (c: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D): Canvas2D => c;
  const asPath = (p: typeof Path2D): Path2DCtor => p;
  assert.equal(typeof asContext, 'function');
  assert.equal(typeof asPath, 'function');
});
