import { avg, dot, faceNormal, mulberry32, neg, norm, sub, type Rng, type Vec3 } from './math.ts';
import { paintOrder, type Layer, type PaintGroup } from './paint-order.ts';
import { VIEW, depthOf } from './projection.ts';
import { TransformStack } from './transform.ts';
import type { Anchor, DisplayList } from './types.ts';

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
  /** Seeded random source; the only randomness generation code may use. */
  readonly rng: Rng;
  readonly anchors: Anchor[] = [];
  private readonly xf: TransformStack;
  private readonly groups: PaintGroup[] = [];
  private current: PaintGroup | null = null;
  private decalCount = 0;
  private fadeWeight = 1;
  private partId: number | undefined;

  constructor(opts: { seed: number; rot?: number; at?: Vec3 }) {
    this.rng = mulberry32(opts.seed);
    this.xf = new TransformStack(opts.rot ?? 0, opts.at ?? [0, 0, 0]);
  }

  /** Enter a local frame rotated by `rot` radians and moved to `at`. */
  push(at: Vec3 = [0, 0, 0], rot = 0) { this.xf.push(at, rot); }

  /** Leave the current local frame. */
  pop() { this.xf.pop(); }

  /** Local point to world point. */
  tf(p: Vec3): Vec3 { return this.xf.point(p); }

  /** Local direction to world direction. */
  td(v: Vec3): Vec3 { return this.xf.direction(v); }

  /** Group the faces added inside `fn`; `pivot` (local) places the group in paint order. */
  object(pivot: Vec3, fn: () => void, opts: { layer?: Layer; ordered?: boolean } = {}) {
    const previous = this.current;
    const group: PaintGroup = { layer: opts.layer ?? 'object', depth: depthOf(this.tf(pivot)), ordered: !!opts.ordered, faces: [] };
    this.groups.push(group);
    this.current = group;
    try { fn(); } finally { this.current = previous; }
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
    group.faces.push({ pts: world, n, mat, tone: o.tone ?? this.facetTone(), depth: this.faceDepth(world, group, o.decalOf), emit: o.emit, alpha: o.alpha, tag: o.tag, fade: this.fadeWeight === 1 ? undefined : this.fadeWeight, part: this.partId, logo: o.logo });
    return group.faces.length - 1;
  }

  /**
   * Show logo `id` flat across a local quad whose corners run top-left, top-right, bottom-right, bottom-left as seen
   * from the front. Pass `decalOf` with the handle of the surface it sits on to paint it right after that surface;
   * `emit` makes it a lit sign at night. Returns a handle, or -1 when the quad faces away.
   */
  logo(corners: [Vec3, Vec3, Vec3, Vec3], id: string, o: { emit?: number; alpha?: number; decalOf?: number } = {}): number {
    return this.face(corners, 'logo', { ...o, logo: id, tone: 0 });
  }

  /** Attach an animation anchor at a local point. */
  anchor(kind: string, at: Vec3, data?: Anchor['data']) {
    this.anchors.push({ kind, at: this.tf(at), data, part: this.partId });
  }

  /** Finish and return faces in paint order plus anchors. */
  build(): DisplayList {
    return { faces: paintOrder(this.groups), anchors: this.anchors };
  }

  private orientNormal(world: Vec3[], o: FaceOpts): Vec3 | null {
    let n = o.n ? norm(this.td(o.n)) : faceNormal(world);
    if (o.inside) { if (dot(n, sub(avg(world), this.tf(o.inside))) < 0) n = neg(n); }
    else if (!o.n && dot(n, VIEW) < 0) n = neg(n);
    const facing = dot(n, VIEW);
    if (o.double) return facing < 0 ? neg(n) : n;
    return facing > 0.002 ? n : null;
  }

  private faceDepth(world: Vec3[], group: PaintGroup, decalOf?: number): number {
    const parent = decalOf !== undefined && decalOf >= 0 ? group.faces[decalOf] : undefined;
    return parent ? parent.depth + 1e-4 * ((++this.decalCount % 500) + 1) : depthOf(avg(world));
  }

  private facetTone(): number {
    const r = this.rng();
    return r < 0.18 ? -1 : r > 0.82 ? 1 : 0;
  }

  private standaloneGroup(world: Vec3[]): PaintGroup {
    const group: PaintGroup = { layer: 'object', depth: depthOf(avg(world)), ordered: false, faces: [] };
    this.groups.push(group);
    return group;
  }
}
