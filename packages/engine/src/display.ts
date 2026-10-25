import type { Anchor, DisplayList, Face } from './types.ts';

/**
 * Keep only the faces and anchors that pass, preserving paint order: every kept anchor's place in the order is
 * moved to count only the faces that were kept.
 */
export function filterDisplayList(dl: DisplayList, keepFace: (f: Face) => boolean, keepAnchor: (a: Anchor) => boolean): DisplayList {
  const keptBefore: number[] = [0], faces: Face[] = [];
  for (const f of dl.faces) { if (keepFace(f)) faces.push(f); keptBefore.push(faces.length); }
  const anchors = dl.anchors.filter(keepAnchor).map(a => (a.after === undefined ? a : { ...a, after: keptBefore[Math.min(a.after, dl.faces.length)] }));
  return { faces, anchors };
}
