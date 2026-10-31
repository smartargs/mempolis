import { project, Z_SCALE } from './projection.ts';
import { hslToRgb, type RGB, type Shader } from './shade.ts';
import type { Anchor, DisplayList, Face, LogoDef, StyleDef } from './types.ts';

export type GlowMotion = 'steady' | 'flicker' | 'blink';

export interface Halo { x: number; y: number; rx: number; ry: number; strength: number; color: RGB; motion: GlowMotion }

export interface SceneCtx { px: number; night: number; glow: number; shader: Shader; style: StyleDef }

export interface SceneSink {
  face(f: Face, fill: string): void;
  logo(f: Face, def: LogoDef): void;
  glow(h: Halo): void;
  looseGlows(hs: Halo[]): void;
  smoke(x: number, y: number, puffs: number): void;
}

export const WARM: RGB = [255, 214, 120];
export const LOGO_NIGHT_DIM = 0.5;
export const NIGHT_GLOW_MIN = 0.05;
const HALO_PAD: readonly [number, number] = [0.35, 0.3];
const HALO_MAX: readonly [number, number] = [1.6, 1.2];

export const tenth = (v: number) => Math.round(v * 10) / 10;
export const hundredth = (v: number) => Math.round(v * 100) / 100;

export function tenthText(v: number): string {
  const n = Math.round(v * 10), m = n < 0 ? -n : n, r = m % 10, s = n < 0 ? '-' : '';
  return r ? `${s}${(m - r) / 10}.${r}` : `${s}${m / 10}`;
}

export const screenX = (p: readonly number[]) => p[0] - p[1];
export const screenY = (p: readonly number[]) => (p[0] + p[1]) * 0.5 - p[2] * Z_SCALE;

export function faceHalo(f: Face, sc: SceneCtx, glow?: [number, number, number]): Halo {
  let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
  for (const p of f.pts) { const x = screenX(p), y = screenY(p); x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  const color = glow ? hslToRgb(...glow) : sc.shader.litColor(f.mat) ?? WARM;
  const rx = Math.min((x1 - x0) / 2 + HALO_PAD[0], HALO_MAX[0]), ry = Math.min((y1 - y0) / 2 + HALO_PAD[1], HALO_MAX[1]);
  return { x: (x0 + x1) / 2, y: (y0 + y1) / 2, rx, ry, strength: (f.emit ?? 0) * sc.night * 0.85 * sc.glow, color, motion: 'steady' };
}

export function anchorHalo(a: Anchor, sc: SceneCtx): Halo | undefined {
  if (a.kind !== 'glow' || sc.night <= NIGHT_GLOW_MIN) return undefined;
  const [x, y] = project(a.at), r = Number(a.data?.r ?? 1);
  const color = (typeof a.data?.mat === 'string' ? sc.shader.litColor(a.data.mat) : undefined) ?? WARM;
  return { x, y, rx: r, ry: r * 0.78, strength: 0.9 * sc.night, color, motion: a.data?.blink ? 'blink' : 'flicker' };
}

export function logoMatrix(f: Face, def: LogoDef, px: number): [number, number, number, number, number, number] {
  const a = f.pts[0], b = f.pts[1], d = f.pts[3];
  const ax = screenX(a), ay = screenY(a);
  return [(screenX(b) - ax) * px / def.w, (screenY(b) - ay) * px / def.w, (screenX(d) - ax) * px / def.h, (screenY(d) - ay) * px / def.h, ax * px, ay * px];
}

export const logoDim = (f: Face, night: number) => night * LOGO_NIGHT_DIM * (1 - (f.emit ?? 0));

export function smokeShapes(x: number, y: number, puffs: number, px: number): { x: number; y: number; r: number; opacity: number }[] {
  return Array.from({ length: puffs }, (_, i) => ({ x: (x + 0.5 * i) * px, y: (y - 1.1 * i) * px, r: (0.16 + 0.05 * i) * px, opacity: 0.85 - i * 0.18 }));
}

export function smokeColors(shader: Shader, night: number): [RGB, RGB] {
  const base: RGB = shader.nightTint(hslToRgb(210, 8, 86), night * 0.8);
  return [base, [base[0] * 0.86, base[1] * 0.88, base[2] * 0.92]];
}

export function hexagon(r: number, from: number, count: number, closeAtCentre: boolean): [number, number][] {
  const pts = Array.from({ length: count }, (_, k): [number, number] => {
    const t = from + (k / 6) * Math.PI * 2;
    return [tenth(Math.cos(t) * r), tenth(Math.sin(t) * r * 0.9)];
  });
  return closeAtCentre ? [...pts, [0, 0]] : pts;
}

export const PUFF_BODY = { from: 0.3, count: 6 } as const;
export const PUFF_SHADE = { from: 0.3 + Math.PI, count: 4 } as const;

export function paintScene(dl: DisplayList, sc: SceneCtx, sink: SceneSink) {
  const n = dl.faces.length, before: (Halo[] | undefined)[] = new Array(n), loose: Halo[] = [], smoke: Anchor[] = [];
  for (const a of dl.anchors) {
    if (a.kind === 'smoke') { smoke.push(a); continue; }
    const h = sc.glow > 0 ? anchorHalo(a, sc) : undefined;
    if (!h) continue;
    if (a.after === undefined || a.after >= n) loose.push(h); else (before[a.after] ??= []).push(h);
  }
  const lit = sc.night > NIGHT_GLOW_MIN && sc.glow > 0, logos = sc.style.logos;
  for (let i = 0; i < n; i++) {
    const f = dl.faces[i], glows = before[i];
    if (glows) for (const h of glows) sink.glow(h);
    if (!f.logo) sink.face(f, sc.shader.fill(f));
    else { const def = logos?.[f.logo]; if (def && f.pts.length >= 4) sink.logo(f, def); }
    if (!f.emit || !lit || f.halo === false) continue;
    const logoGlow = f.logo ? logos?.[f.logo]?.glow : undefined;
    if (!f.logo || logoGlow) sink.glow(faceHalo(f, sc, logoGlow));
  }
  if (loose.length) sink.looseGlows(loose);
  for (const a of smoke) { const [x, y] = project(a.at); sink.smoke(x, y, Number(a.data?.puffs ?? 3)); }
}
