import type { Vec3 } from './math.ts';
import type { PaintGroup } from './paint-order.ts';
import { centroidDepth } from './projection.ts';
import type { Anchor, Face } from './types.ts';

const EPS = 1e-6;
const MIN_AREA = 1e-8;

interface Axis { from: number; n: number }
interface Grid { span: number; x: Axis; y: Axis }

const index = (v: number, span: number) => Math.floor(v / span);

function axis(lo: number, hi: number, span: number): Axis {
  if (hi - lo <= span) return { from: NaN, n: 1 };
  const from = index(lo + EPS, span);
  return { from, n: index(hi - EPS, span) - from + 1 };
}

function gridOver(faces: Face[], span: number): Grid | null {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const f of faces) for (const p of f.pts) { x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); }
  if (x1 - x0 <= span && y1 - y0 <= span) return null;
  return { span, x: axis(x0, x1, span), y: axis(y0, y1, span) };
}

const along = (a: Axis, v: number, span: number) => (a.n === 1 ? 0 : Math.min(a.n - 1, Math.max(0, index(v, span) - a.from)));
const cellOf = (g: Grid, x: number, y: number): [number, number] => [along(g.x, x, g.span), along(g.y, y, g.span)];
const middle = (a: Axis, k: number, at: number, span: number) => (a.n === 1 ? at : (a.from + k + 0.5) * span);

function clipAxis(pts: Vec3[], axis: 0 | 1, at: number, keepAbove: boolean): Vec3[] {
  const out: Vec3[] = [], side = (p: Vec3) => (keepAbove ? p[axis] - at : at - p[axis]);
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], b = pts[(i + 1) % pts.length], sa = side(a), sb = side(b);
    if (sa >= 0) out.push(a);
    if ((sa >= 0) !== (sb >= 0)) {
      const t = sa / (sa - sb);
      out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]);
    }
  }
  return out;
}

function area(pts: Vec3[]): number {
  let x = 0, y = 0, z = 0;
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], b = pts[(i + 1) % pts.length];
    x += a[1] * b[2] - a[2] * b[1]; y += a[2] * b[0] - a[0] * b[2]; z += a[0] * b[1] - a[1] * b[0];
  }
  return Math.hypot(x, y, z) / 2;
}

function convex(pts: Vec3[], n: Vec3): boolean {
  let pos = false, neg = false;
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], b = pts[(i + 1) % pts.length], c = pts[(i + 2) % pts.length];
    const u = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], v = [c[0] - b[0], c[1] - b[1], c[2] - b[2]];
    const turn = (u[1] * v[2] - u[2] * v[1]) * n[0] + (u[2] * v[0] - u[0] * v[2]) * n[1] + (u[0] * v[1] - u[1] * v[0]) * n[2];
    if (turn > EPS) pos = true; else if (turn < -EPS) neg = true;
  }
  return !(pos && neg);
}

const clippable = (f: Face) => !f.logo && (f.alpha ?? 1) >= 1 && f.pts.length >= 3 && convex(f.pts, f.n);

const OVERLAY_GAP = 0.12;
const OVERLAY_TOL = 0.05;

function boundsOf(pts: Vec3[]): [Vec3, Vec3] {
  const lo: Vec3 = [Infinity, Infinity, Infinity], hi: Vec3 = [-Infinity, -Infinity, -Infinity];
  for (const p of pts) for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], p[k]); hi[k] = Math.max(hi[k], p[k]); }
  return [lo, hi];
}

function liesOn(f: Face, e: Face): boolean {
  if (f.n[0] * e.n[0] + f.n[1] * e.n[1] + f.n[2] * e.n[2] < 0.999) return false;
  const o = e.pts[0];
  for (const q of f.pts) if (Math.abs((q[0] - o[0]) * e.n[0] + (q[1] - o[1]) * e.n[1] + (q[2] - o[2]) * e.n[2]) > OVERLAY_GAP) return false;
  const [flo, fhi] = boundsOf(f.pts), [elo, ehi] = boundsOf(e.pts);
  for (let k = 0; k < 3; k++) if (flo[k] < elo[k] - OVERLAY_TOL || fhi[k] > ehi[k] + OVERLAY_TOL) return false;
  return true;
}

function overlay(faces: Face[], at: number): boolean {
  for (let i = 0; i < at; i++) if (liesOn(faces[at], faces[i])) return true;
  return false;
}

function small(f: Face, span: number): boolean {
  const [lo, hi] = boundsOf(f.pts);
  return hi[0] - lo[0] <= 2 * span && hi[1] - lo[1] <= 2 * span;
}

function centreOf(g: Grid, pts: Vec3[]): [number, number] {
  let x = 0, y = 0;
  for (const p of pts) { x += p[0]; y += p[1]; }
  return cellOf(g, x / pts.length, y / pts.length);
}

type Range = [number, number, number, number];
type Box2 = [number, number, number, number];

function flatBox(pts: Vec3[]): Box2 {
  let xa = Infinity, ya = Infinity, xb = -Infinity, yb = -Infinity;
  for (const p of pts) { xa = Math.min(xa, p[0]); ya = Math.min(ya, p[1]); xb = Math.max(xb, p[0]); yb = Math.max(yb, p[1]); }
  return [xa, ya, xb, yb];
}

function range(g: Grid, [xa, ya, xb, yb]: Box2): Range {
  const [i0, j0] = cellOf(g, xa + EPS, ya + EPS), [i1, j1] = cellOf(g, xb - EPS, yb - EPS);
  return [i0, j0, Math.max(i0, i1), Math.max(j0, j1)];
}

function clustered(boxes: Map<number, Box2>): Map<number, Box2> {
  const keys = [...boxes.keys()], root = new Map(keys.map(k => [k, k]));
  const find = (k: number): number => { while (root.get(k) !== k) k = root.get(k)!; return k; };
  const meet = (a: Box2, c: Box2) => a[0] <= c[2] && c[0] <= a[2] && a[1] <= c[3] && c[1] <= a[3];
  for (let i = 0; i < keys.length; i++) for (let j = i + 1; j < keys.length; j++) {
    if (meet(boxes.get(keys[i])!, boxes.get(keys[j])!)) root.set(find(keys[j]), find(keys[i]));
  }
  const union = new Map<number, Box2>();
  for (const k of keys) {
    const r = find(k), b = boxes.get(k)!, u = union.get(r);
    union.set(r, u ? [Math.min(u[0], b[0]), Math.min(u[1], b[1]), Math.max(u[2], b[2]), Math.max(u[3], b[3])] : b);
  }
  return new Map(keys.map(k => [k, union.get(find(k))!]));
}

function* pieces(g: Grid, pts: Vec3[], [i0, j0, i1, j1]: Range): Generator<[number, number, Vec3[]]> {
  const line = (a: Axis, k: number) => (a.from + k) * g.span;
  for (let i = i0; i <= i1; i++) {
    let column = pts;
    if (i > i0) column = clipAxis(column, 0, line(g.x, i), true);
    if (i < i1) column = clipAxis(column, 0, line(g.x, i + 1), false);
    if (column.length < 3) continue;
    for (let j = j0; j <= j1; j++) {
      let cell = column;
      if (j > j0) cell = clipAxis(cell, 1, line(g.y, j), true);
      if (j < j1) cell = clipAxis(cell, 1, line(g.y, j + 1), false);
      if (cell.length >= 3 && area(cell) > MIN_AREA) yield [i, j, cell];
    }
  }
}

interface Cell { faces: Face[]; anchors?: Anchor[]; depth: number }

export interface SplitRoles { decal(f: Face): boolean; root(f: Face): boolean }

export function splitGroup(group: PaintGroup, pivot: Vec3, span: number, roles: SplitRoles, depth: (p: Vec3) => number): PaintGroup[] {
  const g = gridOver(group.faces, span);
  if (!g) return [group];
  const cells = new Map<number, Cell>();
  const depthAt = (i: number, j: number) => depth([middle(g.x, i, pivot[0], span), middle(g.y, j, pivot[1], span), pivot[2]]);
  const cellAt = (i: number, j: number): Cell => {
    const k = j * g.x.n + i;
    let c = cells.get(k);
    if (!c) cells.set(k, c = { faces: [], depth: depthAt(i, j) });
    return c;
  };
  const nearest = ([i0, j0, i1, j1]: Range): Cell => {
    let best: [number, number] = [i0, j0], d = -Infinity;
    for (let i = i0; i <= i1; i++) for (let j = j0; j <= j1; j++) { const e = depthAt(i, j); if (e > d) { d = e; best = [i, j]; } }
    return cellAt(...best);
  };
  const laid = new Map<number, Box2>();
  group.faces.forEach((f, at) => { if (small(f, span) && (roles.decal(f) || (group.ordered && overlay(group.faces, at)))) laid.set(at, flatBox(f.pts)); });
  const spread = clustered(laid);
  group.faces.forEach((f, at) => {
    const over = spread.get(at);
    if (over) { nearest(range(g, over)).faces.push(f); return; }
    const r = range(g, flatBox(f.pts));
    if (r[0] === r[2] && r[1] === r[3]) { cellAt(r[0], r[1]).faces.push(f); return; }
    if (!clippable(f)) { cellAt(...centreOf(g, f.pts)).faces.push(f); return; }
    const glow = f.emit ? centreOf(g, f.pts) : undefined;
    for (const [i, j, pts] of pieces(g, f.pts, r)) {
      const piece: Face = { ...f, pts, depth: roles.root(f) || roles.decal(f) ? f.depth : centroidDepth(pts) };
      if (glow && (glow[0] !== i || glow[1] !== j)) piece.halo = false;
      cellAt(i, j).faces.push(piece);
    }
  });
  for (const a of group.anchors ?? []) (cellAt(...cellOf(g, a.at[0], a.at[1])).anchors ??= []).push(a);
  return [...cells.keys()].sort((a, b) => a - b).map(k => {
    const c = cells.get(k)!;
    return { layer: group.layer, depth: c.depth, ordered: group.ordered, faces: c.faces, ...(c.anchors ? { anchors: c.anchors } : {}) };
  });
}
