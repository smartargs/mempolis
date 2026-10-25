import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import { blob, box, buildAsset, Builder, facetedPlane, gableRoof, hashSeed, mulberry32, prism, renderSVG, type Asset, type AssetCtx, type AssetMapping, type StyleDef } from '../src/index.ts';

const style: StyleDef = {
  id: 'test', steps: 5, light: [-0.38, 0.52, 0.76], ambient: 0.3,
  materials: () => ({ grass: { h: 100, s: 18, l: 58 }, wall: { h: 30, s: 6, l: 84 }, roof: { h: 0, s: 30, l: 46 }, leaf: { h: 120, s: 20, l: 42 }, glass: { h: 210, s: 14, l: 34, lit: [44, 80, 72] } }),
  night: { tint: [0.4, 0.45, 0.65], lift: [6, 8, 22] },
};

const mapping: AssetMapping = { source: 'test', visibility: 'public', cadence: ['weekly'] };

const cottage: Asset<{ storeys: number }> = {
  id: 'cottage', name: 'Cottage', batch: 0, driver: 'test', decayRole: 'maintained', mapping, footprint: [8, 8], stages: [{ label: 'one', params: { storeys: 1 } }],
  build(b, p) {
    b.object([0, 0, -5], () => facetedPlane(b, -4, -4, 4, 4, 0, 2, (_x, _y, r) => (r > 0.5 ? 'grass' : 'leaf')), { layer: 'ground' });
    b.object([0, 0, 0], () => {
      box(b, [-2, -2, 0], [2, 2, 2.8 * p.storeys], 'wall');
      gableRoof(b, -2, -2, 2, 2, 2.8 * p.storeys, 1.6, 'roof', { gable: 'wall' });
    });
  },
};

const grove: Asset<{ trees: number }> = {
  id: 'grove', name: 'Grove', batch: 0, driver: 'test', decayRole: 'none', mapping, footprint: [6, 6], stages: [{ label: 'small', params: { trees: 3 } }],
  build(b, p) {
    for (let i = 0; i < p.trees; i++) {
      const x = (b.rng() - 0.5) * 5, y = (b.rng() - 0.5) * 5, h = 1 + b.rng() * 2;
      b.object([x, y, 0], () => {
        prism(b, [x, y, 0], [x, y, h], 0.2, 0.1, 5, 'wall');
        blob(b, [x, y, h + 0.8], 0.6 + b.rng(), 'leaf', { subdiv: 0 });
      });
    }
  },
};

const ctx: AssetCtx = { region: 'n3', decay: 0.4, lod: 1 };

function hashOf(asset: Asset, params: unknown, seed: number, rot = 0): string {
  const b = new Builder({ seed, rot });
  buildAsset(b, asset, params, ctx);
  return createHash('sha256').update(JSON.stringify(b.build())).digest('hex');
}

test('same seed gives the identical display list', () => {
  for (const [asset, params] of [[cottage, { storeys: 2 }], [grove, { trees: 5 }]] as const) {
    const seed = hashSeed('plot', asset.id);
    assert.equal(hashOf(asset, params, seed), hashOf(asset, params, seed), asset.id);
    assert.equal(hashOf(asset, params, seed, Math.PI / 2), hashOf(asset, params, seed, Math.PI / 2), `${asset.id} rotated`);
  }
});

test('a different seed changes seeded geometry', () => {
  assert.notEqual(hashOf(grove, { trees: 5 }, 1), hashOf(grove, { trees: 5 }, 2));
});

test('the seeded generators are stable across runs', () => {
  assert.equal(hashSeed('street', 'NExample'), hashSeed('street', 'NExample'));
  const a = mulberry32(42), b = mulberry32(42);
  assert.deepEqual(Array.from({ length: 5 }, a), Array.from({ length: 5 }, b));
});

test('rendering the same display list gives the same SVG apart from effect ids', () => {
  const b = new Builder({ seed: 7 });
  buildAsset(b, cottage, { storeys: 1 }, ctx);
  const dl = b.build(), clean = (svg: string) => svg.replace(/glow\d+/g, 'glow');
  assert.equal(clean(renderSVG(dl, style, 'n3', { night: 0.8 })), clean(renderSVG(dl, style, 'n3', { night: 0.8 })));
});

test('a logo face draws its mark across the projected quad, dimmed at night unless lit', () => {
  const branded: StyleDef = { ...style, logos: { mark: { w: 10, h: 5, svg: '<rect width="10" height="5" fill="#00e599"/>' } } };
  const b = new Builder({ seed: 1 });
  b.logo([[0, 2, 2], [2, 0, 2], [2, 0, 1], [0, 2, 1]], 'mark');
  const dl = b.build();
  assert.equal(dl.faces[0].logo, 'mark');
  const day = renderSVG(dl, branded, 'n3', { night: 0 }), night = renderSVG(dl, branded, 'n3', { night: 1 });
  assert.match(day, /<g transform="matrix\([^)]+\)"><rect width="10" height="5" fill="#00e599"\/><\/g>/);
  assert.match(night, /fill="rgb\(6,8,16\)" opacity="0.50"/);
});

test('a logo whose front faces away from the camera is not drawn', () => {
  const b = new Builder({ seed: 1 });
  assert.ok(b.logo([[2, 0, 2], [0, 2, 2], [0, 2, 1], [2, 0, 1]], 'mark') < 0);
  assert.ok(b.logo([[0, 2, 2], [2, 0, 2], [2, 0, 1], [0, 2, 1]], 'mark') >= 0);
});

test('a light behind a nearer wall glows before the wall, so the wall covers its glow', () => {
  const b = new Builder({ seed: 1 });
  b.object([0, 0, 0], () => b.anchor('glow', [0, 0, 1], { r: 1 }));
  b.object([3, 3, 0], () => box(b, [2, 2, 0], [4, 4, 3], 'wall'));
  const dl = b.build(), svg = renderSVG(dl, style, 'n3', { night: 1 });
  assert.equal(dl.anchors[0].after, 0);
  assert.ok(svg.indexOf('<ellipse') < svg.indexOf('<polygon'), 'the glow paints before the wall in front of it');
});
