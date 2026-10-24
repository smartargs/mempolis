import type { Builder, FaceOpts } from './builder.ts';
import { add, avg, cross, mul, norm, sub, type Rng, type Vec2, type Vec3 } from './math.ts';

export function ellipsePts(cx: number, cy: number, rx: number, ry: number, n = 12, rot = 0): Vec2[] {
  const out: Vec2[] = [];
  for (let i = 0; i < n; i++) { const t = rot + (i / n) * Math.PI * 2; out.push([cx + Math.cos(t) * rx, cy + Math.sin(t) * ry]); }
  return out;
}

/** Horizontal polygon at height z, facing up. */
export function flat(b: Builder, pts: Vec2[], z: number, mat: string, o: FaceOpts = {}) {
  return b.face(pts.map(([x, y]) => [x, y, z] as Vec3), mat, { n: [0, 0, 1], ...o });
}

/**
 * A flat rectangle split into jittered triangles: the low-poly ground.
 * `pick` chooses the material per triangle (e.g. patches of dry grass).
 */
export function facetedPlane(b: Builder, x0: number, y0: number, x1: number, y1: number, z: number, cell: number, pick: (x: number, y: number, r: number) => string, jitter = 0.28) {
  const nx = Math.max(1, Math.round((x1 - x0) / cell)), ny = Math.max(1, Math.round((y1 - y0) / cell));
  const dx = (x1 - x0) / nx, dy = (y1 - y0) / ny;
  const P: Vec2[][] = [];
  for (let i = 0; i <= nx; i++) {
    P.push([]);
    for (let j = 0; j <= ny; j++) {
      const edge = i === 0 || j === 0 || i === nx || j === ny;
      const jx = edge ? 0 : (b.rng() - 0.5) * 2 * jitter * dx, jy = edge ? 0 : (b.rng() - 0.5) * 2 * jitter * dy;
      P[i].push([x0 + i * dx + jx, y0 + j * dy + jy]);
    }
  }
  for (let i = 0; i < nx; i++) for (let j = 0; j < ny; j++) {
    const a = P[i][j], c = P[i + 1][j], d = P[i + 1][j + 1], e = P[i][j + 1];
    const tris: Vec2[][] = (i + j) % 2 ? [[a, c, d], [a, d, e]] : [[a, c, e], [c, d, e]];
    for (const t of tris) {
      const cx = (t[0][0] + t[1][0] + t[2][0]) / 3, cy = (t[0][1] + t[1][1] + t[2][1]) / 3;
      flat(b, t, z, pick(cx, cy, b.rng()));
    }
  }
}

/** Face handles of a box, for placing decals. */
export interface BoxFaces { top: number; xn: number; xp: number; yn: number; yp: number }

/** Axis-aligned box in local space (no bottom face). Returns face handles for decals. */
export function box(b: Builder, min: Vec3, max: Vec3, mat: string, o: { top?: string; sides?: string } = {}): BoxFaces {
  const [x0, y0, z0] = min, [x1, y1, z1] = max;
  const inside: Vec3 = [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2];
  const side = o.sides ?? mat;
  return {
    yp: b.face([[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]], side, { n: [0, 1, 0] }),
    xp: b.face([[x1, y0, z0], [x1, y1, z0], [x1, y1, z1], [x1, y0, z1]], side, { n: [1, 0, 0] }),
    yn: b.face([[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]], side, { n: [0, -1, 0] }),
    xn: b.face([[x0, y0, z0], [x0, y1, z0], [x0, y1, z1], [x0, y0, z1]], side, { n: [-1, 0, 0] }),
    top: b.face([[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]], o.top ?? mat, { n: [0, 0, 1], inside }),
  };
}

/**
 * Pitched roof over the rectangle x0..x1, y0..y1 with the ridge along x.
 * Gable ends are filled with `gable` (usually the wall material).
 */
export function gableRoof(b: Builder, x0: number, y0: number, x1: number, y1: number, zEave: number, rise: number, mat: string,
  o: { over?: number; gable?: string; edge?: string; edgeT?: number } = {}) {
  const ov = o.over ?? 0.35, yc = (y0 + y1) / 2, half = (y1 - y0) / 2, zR = zEave + rise;
  const slope = rise / half, zLow = zEave - ov * slope;
  const inside: Vec3 = [(x0 + x1) / 2, yc, zEave];
  if (o.gable) {
    b.face([[x0, y0, zEave], [x0, y1, zEave], [x0, yc, zR]], o.gable, { n: [-1, 0, 0] });
    b.face([[x1, y0, zEave], [x1, y1, zEave], [x1, yc, zR]], o.gable, { n: [1, 0, 0] });
  }
  const s1 = b.face([[x0 - ov, y1 + ov, zLow], [x1 + ov, y1 + ov, zLow], [x1 + ov, yc, zR], [x0 - ov, yc, zR]], mat, { inside });
  const s2 = b.face([[x0 - ov, y0 - ov, zLow], [x1 + ov, y0 - ov, zLow], [x1 + ov, yc, zR], [x0 - ov, yc, zR]], mat, { inside });
  if (o.edge) {
    const t = o.edgeT ?? 0.12;
    b.face([[x0 - ov, y1 + ov, zLow], [x1 + ov, y1 + ov, zLow], [x1 + ov, y1 + ov, zLow - t], [x0 - ov, y1 + ov, zLow - t]], o.edge, { n: [0, 1, 0] });
    b.face([[x0 - ov, y0 - ov, zLow], [x1 + ov, y0 - ov, zLow], [x1 + ov, y0 - ov, zLow - t], [x0 - ov, y0 - ov, zLow - t]], o.edge, { n: [0, -1, 0] });
    for (const [x, sx] of [[x1 + ov, 1], [x0 - ov, -1]] as const) {
      b.face([[x, y1 + ov, zLow], [x, yc, zR], [x, yc, zR - t], [x, y1 + ov, zLow - t]], o.edge, { n: [sx, 0, 0] });
      b.face([[x, y0 - ov, zLow], [x, yc, zR], [x, yc, zR - t], [x, y0 - ov, zLow - t]], o.edge, { n: [sx, 0, 0] });
    }
  }
  return { s1, s2, zRidge: zR };
}

/** Tapered faceted column from A to B (trunks, branches, posts, chimneys). */
export function prism(b: Builder, A: Vec3, B: Vec3, ra: number, rb: number, sides: number, mat: string,
  o: { jit?: number; cap?: boolean; rot?: number; rng?: Rng } = {}) {
  const rng = o.rng ?? b.rng, jit = o.jit ?? 0.18, rot = o.rot ?? 0.3;
  const d = norm(sub(B, A));
  const p = Math.abs(d[2]) > 0.98 ? norm(cross(d, [1, 0, 0])) : norm(cross(d, [0, 0, 1]));
  const q = cross(d, p);
  const rA: Vec3[] = [], rB: Vec3[] = [];
  for (let i = 0; i < sides; i++) {
    const t = rot + (i / sides) * Math.PI * 2, cs = Math.cos(t), sn = Math.sin(t);
    const dir: Vec3 = [p[0] * cs + q[0] * sn, p[1] * cs + q[1] * sn, p[2] * cs + q[2] * sn];
    rA.push(add(A, mul(dir, ra * (1 + (rng() - 0.5) * jit))));
    rB.push(add(B, mul(dir, rb * (1 + (rng() - 0.5) * jit))));
  }
  const mid = avg([A, B]);
  for (let i = 0; i < sides; i++) {
    const j = (i + 1) % sides;
    b.face([rA[i], rA[j], rB[j], rB[i]], mat, { inside: mid });
  }
  if (o.cap) b.face(rB, mat, { inside: mid });
}

const icoCache = new Map<number, { v: Vec3[]; f: [number, number, number][] }>();
/** Icosphere mesh with `subdiv` subdivisions (cached, unit radius). */
export function icoMesh(subdiv: number) {
  const hit = icoCache.get(subdiv); if (hit) return hit;
  const t = (1 + Math.sqrt(5)) / 2;
  const v: Vec3[] = ([[-1, t, 0], [1, t, 0], [-1, -t, 0], [1, -t, 0], [0, -1, t], [0, 1, t], [0, -1, -t], [0, 1, -t], [t, 0, -1], [t, 0, 1], [-t, 0, -1], [-t, 0, 1]] as Vec3[]).map(norm);
  let f: [number, number, number][] = [[0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11], [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6], [7, 1, 8], [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9], [4, 9, 5], [2, 4, 11], [6, 2, 10], [8, 6, 7], [9, 8, 1]];
  for (let s = 0; s < subdiv; s++) {
    const cache = new Map<string, number>(), nf: [number, number, number][] = [];
    const mid = (a: number, c: number) => { const k = a < c ? `${a}_${c}` : `${c}_${a}`; const h = cache.get(k); if (h !== undefined) return h; v.push(norm(avg([v[a], v[c]]))); cache.set(k, v.length - 1); return v.length - 1; };
    for (const [a, c, d] of f) { const ab = mid(a, c), bc = mid(c, d), ca = mid(d, a); nf.push([a, ab, ca], [c, bc, ab], [d, ca, bc], [ab, bc, ca]); }
    f = nf;
  }
  const m = { v, f }; icoCache.set(subdiv, m); return m;
}

/** Faceted sphere-ish blob (tree crowns, bushes, smoke, boulders). */
export function blob(b: Builder, C: Vec3, r: number, mat: string,
  o: { sx?: number; sy?: number; sz?: number; subdiv?: number; jit?: number; rot?: number; rng?: Rng; flatBottom?: number } = {}) {
  const rng = o.rng ?? b.rng, mesh = icoMesh(o.subdiv ?? (r > 0.9 ? 1 : 0)), jit = o.jit ?? 0.12;
  const rot = o.rot ?? rng() * 6.283, cs = Math.cos(rot), sn = Math.sin(rot);
  const sx = o.sx ?? 1, sy = o.sy ?? 1, sz = o.sz ?? 1;
  const verts = mesh.v.map(p => {
    const k = 1 + (rng() - 0.5) * 2 * jit;
    const x = p[0] * cs - p[1] * sn, y = p[0] * sn + p[1] * cs;
    let z = p[2] * r * sz * k;
    if (o.flatBottom !== undefined) z = Math.max(z, -o.flatBottom * r * sz);
    return [C[0] + x * r * sx * k, C[1] + y * r * sy * k, C[2] + z] as Vec3;
  });
  for (const [a, c, d] of mesh.f) b.face([verts[a], verts[c], verts[d]], mat, { inside: C });
}

/** Small gem shape: flowers, buds, sparks. */
export function octa(b: Builder, C: Vec3, r: number, mat: string, o: { sz?: number; rot?: number; emit?: number } = {}) {
  const sz = o.sz ?? 1, [cx, cy, cz] = C;
  const top: Vec3 = [cx, cy, cz + r * 1.15 * sz], bot: Vec3 = [cx, cy, cz - r * 0.8 * sz];
  const ring: Vec3[] = [];
  for (let i = 0; i < 4; i++) { const t = Math.PI / 4 + (i * Math.PI) / 2 + (o.rot ?? 0); ring.push([cx + Math.cos(t) * r, cy + Math.sin(t) * r, cz + r * 0.1]); }
  for (let i = 0; i < 4; i++) {
    const j = (i + 1) % 4;
    b.face([top, ring[i], ring[j]], mat, { inside: C, emit: o.emit });
    b.face([bot, ring[j], ring[i]], mat, { inside: C, emit: o.emit });
  }
}

/** Rectangle on one side of a box, nudged outward: windows, doors, signs. */
export function wallRect(side: 'xn' | 'xp' | 'yn' | 'yp', min: Vec3, max: Vec3, u0: number, u1: number, z0: number, z1: number, out = 0.02): Vec3[] {
  switch (side) {
    case 'yp': return [[u0, max[1] + out, z0], [u1, max[1] + out, z0], [u1, max[1] + out, z1], [u0, max[1] + out, z1]];
    case 'yn': return [[u0, min[1] - out, z0], [u1, min[1] - out, z0], [u1, min[1] - out, z1], [u0, min[1] - out, z1]];
    case 'xp': return [[max[0] + out, u0, z0], [max[0] + out, u1, z0], [max[0] + out, u1, z1], [max[0] + out, u0, z1]];
    case 'xn': return [[min[0] - out, u0, z0], [min[0] - out, u1, z0], [min[0] - out, u1, z1], [min[0] - out, u0, z1]];
  }
}
/** Outward normal for each box side. */
export const SIDE_NORMAL: Record<'xn' | 'xp' | 'yn' | 'yp', Vec3> = { xn: [-1, 0, 0], xp: [1, 0, 0], yn: [0, -1, 0], yp: [0, 1, 0] };
