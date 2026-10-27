import type { StyleDef } from '../src/index.ts';

declare module '../src/index.ts' {
  interface RegionIds { n3: true; neox: true }
}

export const style: StyleDef = {
  id: 'fixture', steps: 5, light: [-0.38, 0.52, 0.76], ambient: 0.3,
  materials: () => ({
    wall: { h: 30, s: 6, l: 84 }, roof: { h: 0, s: 30, l: 46 },
    glass: { h: 210, s: 14, l: 34, lit: [44, 80, 72] }, sign: { h: 160, s: 60, l: 40, lit: [160, 90, 60] },
  }),
  night: { tint: [0.4, 0.45, 0.65], lift: [6, 8, 22] },
  logos: {
    mark: { w: 10, h: 5, svg: '<rect width="10" height="5" rx="1" fill="#00e599"/><path d="M1 1h2v2z" fill-rule="evenodd" opacity="0.5"/><circle cx="5" cy="2" r="1" fill="none" stroke="#fff" stroke-width="0.5" stroke-linecap="round"/>' },
    lit: { w: 4, h: 4, svg: '<path fill="#f0f" d="M0 0h4v4H0z"/>', glow: [300, 90, 60] },
  },
};
