import { cross, dot, faceNormal, mulberry32, negate, norm, normalize, sub, type Rng, type Vec3 } from './math.ts';
import { paintLayout, type Layer, type PaintGroup } from './paint-order.ts';
import { VIEW, centroidDepth, depthOf } from './projection.ts';
import { splitGroup } from './split.ts';
import { TransformStack } from './transform.ts';
import type { Anchor, DisplayList, Face } from './types.ts';

const STACK_SPREAD = 1e-4;
const DECAL_STEP = 1e-6;

const towardCentroid = (n: Vec3, ps: Vec3[], from: Vec3): number => {
  let x = 0, y = 0, z = 0;
  for (const p of ps) { x += p[0]; y += p[1]; z += p[2]; }
  const k = 1 / ps.length;
  return n[0] * (x * k - from[0]) + n[1] * (y * k - from[1]) + n[2] * (z * k - from[2]);
};

/** Options for a single face. */
export interface FaceOpts {
  /** Normal in local space; skips automatic normal computation. */
  n?: Vec3;
  /** A local point inside the solid; the normal is flipped to point away from it. */
  inside?: Vec3;
  /** Keep the face even when it points away from the camera. */
  double?: boolean;
  /** Light output 0..1 for emissive materials. */
  emit?: number;
  /** False keeps a lit face crisp at night: it shows its lit colour but casts no halo, as small lit text needs. */
  halo?: false;
  alpha?: number;
  /** Fixed shading-step offset instead of the seeded facet jitter. */
  tone?: number;
  /** Handle of an earlier face in the same group to draw directly on top of. */
  decalOf?: number;
  tag?: string;
  /** Id of a logo in the style to draw across this quad instead of a fill. */
  logo?: string;
}

/**
 * Collects shaded faces and animation anchors for one asset or plot.
 * Geometry is authored in local metres and placed through a transform stack,
 * so assets can face any direction while the light stays fixed.
 */
export class Builder {
  private random: Rng;
  readonly anchors: Anchor[] = [];
  private readonly xf: TransformStack;
  private readonly groups: PaintGroup[] = [];
  private current: PaintGroup | null = null;
  private readonly decalRoot = new Map<Face, Face>();
  private readonly decalCount = new Map<Face, number>();
  private fadeWeight = 1;
  private partId: number | undefined;
  private band: number | undefined;

  constructor(opts: { seed: number; rot?: number; at?: Vec3 }) {
    this.random = mulberry32(opts.seed);
    this.xf = new TransformStack(opts.rot ?? 0, opts.at ?? [0, 0, 0]);
  }

  /** Seeded random source; the only randomness generation code may use. */
  get rng(): Rng { return this.random; }

  /**
   * Run `fn` with a fresh random source seeded by `seed` and restore the previous one after, so what `fn` builds is
   * the same wherever and after whatever it is built. Returns what `fn` returns.
   */
  seeded<T>(seed: number, fn: () => T): T {
    const previous = this.random;
    this.random = mulberry32(seed);
    try { return fn(); } finally { this.random = previous; }
  }

  /** Enter a local frame rotated by `rot` radians and moved to `at`. */
  push(at: Vec3 = [0, 0, 0], rot = 0) { this.xf.push(at, rot); }

  /** Leave the current local frame. */
  pop() { this.xf.pop(); }

  /** Local point to world point. */
  tf(p: Vec3): Vec3 { return this.xf.point(p); }

  /** Local direction to world direction. */
  td(v: Vec3): Vec3 { return this.xf.direction(v); }

  /**
   * Group the faces added inside `fn`; `pivot` (local) places the group in paint order. With `split`, a group wider
   * than that many metres along world x or y is cut along a world grid of that pitch, and each piece sorts at the
   * middle of its grid cell at the pivot's height, so a long kerb, rail or roof never paints its far end over something
   * nearer. Small decals and strips laid on an earlier face stay whole and paint after the pieces under them; ground
   * groups are never cut.
   */
  object(pivot: Vec3, fn: () => void, opts: { layer?: Layer; ordered?: boolean; split?: number } = {}) {
    const previous = this.current, at = this.tf(pivot);
    const group: PaintGroup = { layer: opts.layer ?? 'object', depth: this.groupDepth(depthOf(at)), ordered: !!opts.ordered, faces: [] };
    const index = this.groups.push(group) - 1;
    this.current = group;
    try { fn(); } finally { this.current = previous; }
    if (opts.split && group.layer !== 'ground') this.splitInPlace(index, at, opts.split);
  }

  /**
   * Paint everything `fn` adds as one unit at the depth of the local point `at`: its groups keep their order among
   * themselves but all sort as if they stood at `at`. Use it for a lot or a building on a grid, so tall parts of a
   * unit behind can never be painted over a unit in front.
   */
  stack(at: Vec3, fn: () => void) {
    const previous = this.band;
    this.band = depthOf(this.tf(at));
    try { fn(); } finally { this.band = previous; }
  }

  /** Run `fn` with a fade weight applied to every face it adds. */
  scope(opts: { fade: number }, fn: () => void) {
    const previous = this.fadeWeight;
    this.fadeWeight = opts.fade;
    try { fn(); } finally { this.fadeWeight = previous; }
  }

  /** Mark every face and anchor added inside `fn` with part `id`, so the display list can later be split by part. */
  part(id: number, fn: () => void) {
    const previous = this.partId;
    this.partId = id;
    try { fn(); } finally { this.partId = previous; }
  }

  /** Add a polygon in local space. Returns a handle for decals, or -1 when culled. */
  face(pts: Vec3[], mat: string, o: FaceOpts = {}): number {
    const world = pts.map(p => this.tf(p));
    const n = this.orientNormal(world, o);
    if (!n) return -1;
    const group = this.current ?? this.standaloneGroup(world);
    const parent = o.decalOf !== undefined && o.decalOf >= 0 ? group.faces[o.decalOf] : undefined;
    const root = parent && (this.decalRoot.get(parent) ?? parent);
    const face: Face = { pts: world, n, mat, tone: o.tone ?? this.facetTone(), depth: root ? this.decalDepth(root) : centroidDepth(world), emit: o.emit, ...(o.halo === false ? { halo: false as const } : {}), alpha: o.alpha, tag: o.tag, fade: this.fadeWeight === 1 ? undefined : this.fadeWeight, part: this.partId, logo: o.logo };
    group.faces.push(face);
    if (root) this.decalRoot.set(face, root);
    return group.faces.length - 1;
  }

  /**
   * Show logo `id` flat across a local quad whose corners run top-left, top-right, bottom-right, bottom-left as seen
   * from the front. Pass `decalOf` with the handle of the surface it sits on to paint it right after that surface;
   * `emit` makes it a lit sign at night. Returns a handle, or -1 when the front faces away from the camera.
   */
  logo(corners: [Vec3, Vec3, Vec3, Vec3], id: string, o: { emit?: number; alpha?: number; decalOf?: number } = {}): number {
    const [tl, tr, , bl] = corners;
    return this.face(corners, 'logo', { ...o, logo: id, tone: 0, n: norm(cross(sub(tr, tl), sub(bl, tl))) });
  }

  /** Attach an animation anchor at a local point. */
  anchor(kind: string, at: Vec3, data?: Anchor['data']) {
    const anchor: Anchor = { kind, at: this.tf(at), data, part: this.partId };
    this.anchors.push(anchor);
    const group = this.current ?? this.anchorGroup(anchor.at);
    (group.anchors ??= []).push(anchor);
  }

  /** Finish and return faces in paint order plus anchors. */
  build(): DisplayList {
    return paintLayout(this.groups);
  }

  private orientNormal(world: Vec3[], o: FaceOpts): Vec3 | null {
    const n = o.n ? normalize(this.td(o.n)) : faceNormal(world);
    if (o.inside) { if (towardCentroid(n, world, this.tf(o.inside)) < 0) negate(n); }
    else if (!o.n && dot(n, VIEW) < 0) negate(n);
    const facing = dot(n, VIEW);
    if (o.double) return facing < 0 ? negate(n) : n;
    return facing > 0.002 ? n : null;
  }

  private decalDepth(root: Face): number {
    const k = (this.decalCount.get(root) ?? 0) + 1;
    this.decalCount.set(root, k);
    return root.depth + DECAL_STEP * k;
  }

  private facetTone(): number {
    const r = this.rng();
    return r < 0.18 ? -1 : r > 0.82 ? 1 : 0;
  }

  private groupDepth(raw: number): number {
    return this.band === undefined ? raw : this.band + raw * STACK_SPREAD;
  }

  private splitInPlace(index: number, at: Vec3, span: number) {
    const roles = { decal: (f: Face) => this.decalRoot.has(f), root: (f: Face) => this.decalCount.has(f) };
    const parts = splitGroup(this.groups[index], at, span, roles, p => this.groupDepth(depthOf(p)));
    this.groups[index] = parts[0];
    for (let i = 1; i < parts.length; i++) this.groups.push(parts[i]);
  }

  private anchorGroup(at: Vec3): PaintGroup {
    const group: PaintGroup = { layer: 'object', depth: this.groupDepth(depthOf(at)), ordered: true, faces: [] };
    this.groups.push(group);
    return group;
  }

  private standaloneGroup(world: Vec3[]): PaintGroup {
    const group: PaintGroup = { layer: 'object', depth: this.groupDepth(centroidDepth(world)), ordered: false, faces: [] };
    this.groups.push(group);
    return group;
  }
}
