import { blob, box, facetedPlane, gableRoof, prism, wallRect, type ArtPack, type Asset, type Material, type StyleDef } from '@neoworld/engine';

const mats: Record<string, Material> = {
  grass: { h: 100, s: 18, l: 58 }, grassDry: { h: 60, s: 16, l: 62 }, soil: { h: 30, s: 14, l: 44 },
  wall: { h: 30, s: 6, l: 84 }, roof: { h: 0, s: 30, l: 46 }, door: { h: 210, s: 20, l: 36 },
  glass: { h: 210, s: 14, l: 34, lit: [44, 80, 72] }, trunk: { h: 25, s: 20, l: 34 }, leaf: { h: 120, s: 20, l: 42 },
};

export const referenceStyle: StyleDef = {
  id: 'reference', steps: 5, light: [-0.38, 0.52, 0.76], ambient: 0.3,
  materials: () => mats, night: { tint: [0.4, 0.45, 0.65], lift: [6, 8, 22] },
};

const house: Asset<{ storeys: number; lit: number }> = {
  id: 'house-basic', name: 'Basic house', batch: 1, decayRole: 'none', mapping: { source: 'Activity level', visibility: 'public', cadence: ['weekly'] }, driver: 'Reference: storeys by activity level.', footprint: [8, 8],
  stages: [{ label: 'One storey', params: { storeys: 1, lit: 0.3 } }, { label: 'Two storeys', params: { storeys: 2, lit: 1 } }],
  build(b, p) {
    b.object([0, 0, -5], () => facetedPlane(b, -4, -4, 4, 4, 0, 4, () => 'grass'), { layer: 'ground' });
    const h = 2.8 * p.storeys, min = [-2.5, -2, 0] as [number, number, number], max = [2.5, 2, h] as [number, number, number];
    b.object([0, 0, 0], () => {
      const f = box(b, min, max, 'wall');
      b.face(wallRect('yp', min, max, -0.5, 0.5, 0, 2.1), 'door', { n: [0, 1, 0], decalOf: f.yp });
      for (let s = 0; s < p.storeys; s++) for (const u of [-1.6, 1.6]) b.face(wallRect('yp', min, max, u - 0.45, u + 0.45, 2.8 * s + 1, 2.8 * s + 2.1), 'glass', { n: [0, 1, 0], decalOf: f.yp, emit: p.lit });
      gableRoof(b, -2.5, -2, 2.5, 2, h, 1.8, 'roof', { gable: 'wall' });
    });
  },
};

const tree: Asset<{ age: number }> = {
  id: 'tree-basic', name: 'Basic tree', batch: 1, decayRole: 'none', mapping: { source: 'Holding duration', visibility: 'public', cadence: ['weekly'] }, driver: 'Reference: size by age.', footprint: [4, 4],
  stages: [{ label: 'Young', params: { age: 0.3 } }, { label: 'Old', params: { age: 1 } }],
  build(b, p) {
    b.object([0, 0, 0], () => {
      prism(b, [0, 0, 0], [0, 0, 1 + 2 * p.age], 0.1 + 0.2 * p.age, 0.08, 5, 'trunk');
      blob(b, [0, 0, 1.6 + 2.6 * p.age], 0.6 + 1.2 * p.age, 'leaf', { subdiv: 0 });
    });
  },
};

export const pack: ArtPack = { id: 'reference', name: 'Reference art (open source)', style: referenceStyle, assets: [house, tree] };
export default pack;
