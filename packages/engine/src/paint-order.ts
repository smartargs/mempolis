import type { Face } from './types.ts';

/** Drawing layers, painted in this order. */
export type Layer = 'ground' | 'object' | 'top';

/** A group of faces painted together at one depth. */
export interface PaintGroup {
  layer: Layer;
  depth: number;
  /** Keep insertion order instead of sorting faces by depth. */
  ordered: boolean;
  faces: Face[];
}

const LAYER_RANK: Record<Layer, number> = { ground: 0, object: 1, top: 2 };

/**
 * Painter's order: layers first; ground groups in insertion order; other groups far to near;
 * faces inside a group far to near unless the group is ordered.
 */
export function paintOrder(groups: PaintGroup[]): Face[] {
  const live = groups.filter(g => g.faces.length);
  live.sort((a, b) => LAYER_RANK[a.layer] - LAYER_RANK[b.layer] || (a.layer === 'ground' ? 0 : a.depth - b.depth));
  const faces: Face[] = [];
  for (const g of live) faces.push(...(g.ordered ? g.faces : g.faces.slice().sort((a, b) => a.depth - b.depth)));
  return faces;
}
