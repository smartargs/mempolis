import type { Vec3 } from './math.ts';

interface Frame { x: number; y: number; z: number; c: number; s: number }

/** A stack of rotate-around-z-then-translate frames mapping local metres to world metres. */
export class TransformStack {
  private frames: Frame[];

  constructor(rot = 0, at: Vec3 = [0, 0, 0]) {
    this.frames = [{ x: at[0], y: at[1], z: at[2], c: Math.cos(rot), s: Math.sin(rot) }];
  }

  /** Enter a child frame: rotate by `rot` radians, then move to the local point `at`. */
  push(at: Vec3 = [0, 0, 0], rot = 0) {
    const t = this.top(), p = this.point(at), c = Math.cos(rot), s = Math.sin(rot);
    this.frames.push({ x: p[0], y: p[1], z: p[2], c: t.c * c - t.s * s, s: t.s * c + t.c * s });
  }

  /** Leave the current child frame. The root frame is never removed. */
  pop() {
    if (this.frames.length > 1) this.frames.pop();
  }

  /** Map a local point to world space. */
  point(p: Vec3): Vec3 {
    const t = this.top();
    return [t.x + p[0] * t.c - p[1] * t.s, t.y + p[0] * t.s + p[1] * t.c, t.z + p[2]];
  }

  /** Map a local direction to world space (rotation only). */
  direction(v: Vec3): Vec3 {
    const t = this.top();
    return [v[0] * t.c - v[1] * t.s, v[0] * t.s + v[1] * t.c, v[2]];
  }

  private top(): Frame {
    return this.frames[this.frames.length - 1];
  }
}
