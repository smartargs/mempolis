import { clamp, dot, lerp, norm } from './math.ts';
import type { Face, Material, Region, StyleDef } from './types.ts';

/** RGB channels 0..255. */
export type RGB = [number, number, number];

/** HSL (degrees, percent, percent) to RGB 0..255. */
export function hslToRgb(h: number, s: number, l: number): RGB {
  h = ((h % 360) + 360) % 360; s = clamp(s, 0, 100) / 100; l = clamp(l, 0, 100) / 100;
  const c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = l - c / 2;
  const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
  return [(r + m) * 255, (g + m) * 255, (b + m) * 255];
}
const COOL: RGB = [0.9, 0.95, 1.08];
const WARM: RGB = [1.04, 1.01, 0.94];
const temper = (c: RGB, tint: RGB, k: number): RGB => [c[0] * lerp(1, tint[0], k), c[1] * lerp(1, tint[1], k), c[2] * lerp(1, tint[2], k)];
/** CSS rgb() string. */
export const rgbCss = (c: RGB) => `rgb(${c[0] | 0},${c[1] | 0},${c[2] | 0})`;
const HEX = Array.from({ length: 256 }, (_, i) => i.toString(16).padStart(2, '0'));
const byte = (v: number) => HEX[Math.max(0, Math.min(255, v | 0))];
/** CSS `#rrggbb` string of the same colour `rgbCss` writes. */
export const rgbHex = (c: RGB) => `#${byte(c[0])}${byte(c[1])}${byte(c[2])}`;

/**
 * Quantised low-poly shading. Each material gets a ramp of `steps` colours
 * (hue-preserving: cooler and darker in shadow, warmer in light); a face picks its step from
 * its angle to the light plus a small facet offset.
 */
export class Shader {
  private readonly ramps = new Map<string, RGB[]>();
  private readonly fills = new Map<string, string[]>();
  private readonly mats: Record<string, Material>;
  private readonly light: [number, number, number];
  private readonly style: StyleDef;
  private readonly o: { night?: number; fade?: number };

  constructor(style: StyleDef, region: Region, o: { night?: number; fade?: number } = {}) {
    this.style = style;
    this.o = o;
    this.mats = style.materials(region);
    this.light = norm(style.light);
  }
  /** The shading ramp of a material, dark to light. */
  ramp(mat: string): RGB[] {
    const hit = this.ramps.get(mat); if (hit) return hit;
    const m = this.mats[mat] ?? { h: 300, s: 90, l: 55 };
    const n = this.style.steps, out: RGB[] = [];
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1);
      const s = m.s * (1.1 - 0.2 * t);
      const l = clamp(m.l * (0.55 + 0.6 * t) + 3 * t, 2, 97);
      const base = hslToRgb(m.h, s, l);
      out.push(t < 0.6 ? temper(base, COOL, (0.6 - t) / 0.6) : temper(base, WARM, (t - 0.6) / 0.4));
    }
    this.ramps.set(mat, out); return out;
  }
  /** Final colour of a face, including fade, night tint and emission. */
  color(f: Face): RGB {
    const ramp = this.ramp(f.mat);
    let c = ramp[this.step(f, ramp.length)];
    const fade = this.fadeOf(f), night = this.o.night ?? 0;
    if (fade > 0) {
      const g = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2], a = 0.55 * fade;
      c = [lerp(c[0], g * 1.05 + 6, a), lerp(c[1], g + 4, a), lerp(c[2], g * 0.9, a)];
    }
    if (night > 0) c = this.nightTint(c, night);
    const m = this.mats[f.mat];
    if (f.emit && m?.lit) {
      const lit = hslToRgb(m.lit[0], m.lit[1], m.lit[2]), a = clamp(f.emit * lerp(0.3, 1, night), 0, 1);
      c = [lerp(c[0], lit[0], a), lerp(c[1], lit[1], a), lerp(c[2], lit[2], a)];
    }
    return c;
  }

  /** `color` as a CSS `#rrggbb` string, cached per material step for faces without fade or emission. */
  fill(f: Face): string {
    if (this.fadeOf(f) > 0 || (f.emit && this.mats[f.mat]?.lit)) return rgbHex(this.color(f));
    const ramp = this.ramp(f.mat), idx = this.step(f, ramp.length);
    let row = this.fills.get(f.mat);
    if (!row) { row = []; this.fills.set(f.mat, row); }
    return (row[idx] ??= rgbHex(this.color(f)));
  }

  private step(f: Face, n: number): number {
    const k = clamp((dot(f.n, this.light) + 0.35) / 1.35, 0, 1);
    return clamp(Math.round(k * (n - 1)) + f.tone, 0, n - 1);
  }

  private fadeOf(f: Face): number {
    return (this.o.fade ?? 0) * (f.fade ?? 1);
  }

  /** The lit colour of an emissive material, if it has one. */
  litColor(mat: string): RGB | undefined {
    const lit = this.mats[mat]?.lit;
    return lit ? hslToRgb(lit[0], lit[1], lit[2]) : undefined;
  }

  /** Apply the style's night tint to a colour. */
  nightTint(c: RGB, night: number): RGB {
    const t = this.style.night.tint, l = this.style.night.lift;
    return [c[0] * lerp(1, t[0], night) + l[0] * night, c[1] * lerp(1, t[1], night) + l[1] * night, c[2] * lerp(1, t[2], night) + l[2] * night];
  }
}
