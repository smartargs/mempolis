import { norm, type Vec2, type Vec3 } from './math.ts';

/** Direction from the scene toward the camera (fixed isometric view). */
export const VIEW: Vec3 = norm([1, 1, 0.8165]);
/** Vertical scale of the 2:1 isometric projection, relative to one horizontal unit. */
export const Z_SCALE = 1.2247;

/** World metres to screen units (multiply by pixels per metre to get pixels). */
export function project(p: Vec3): Vec2 {
  return [p[0] - p[1], (p[0] + p[1]) * 0.5 - p[2] * Z_SCALE];
}

/** Larger = closer to the camera. Painter's algorithm draws ascending. */
export const depthOf = (p: Vec3) => p[0] * VIEW[0] + p[1] * VIEW[1] + p[2] * VIEW[2];

/** Depth of the centroid of `ps`, equal to `depthOf(avg(ps))` without allocating. */
export function centroidDepth(ps: Vec3[]): number {
  let x = 0, y = 0, z = 0;
  for (const p of ps) { x += p[0]; y += p[1]; z += p[2]; }
  const k = 1 / ps.length;
  return x * k * VIEW[0] + y * k * VIEW[1] + z * k * VIEW[2];
}
