import { hslToRgb, rgbCss, type RGB, type Shader } from './shade.ts';
import type { Anchor } from './types.ts';

const WARM: RGB = [255, 214, 120];

/** Radial glow gradients for one render, one per distinct light colour. */
export class GlowPalette {
  private readonly ids = new Map<string, string>();
  private readonly prefix: string;

  constructor(prefix: string) {
    this.prefix = prefix;
  }

  /** The gradient id for a light colour, registering it on first use. */
  id(c: RGB): string {
    const key = c.map(v => Math.round(v)).join(',');
    let id = this.ids.get(key);
    if (!id) { id = `${this.prefix}-${this.ids.size}`; this.ids.set(key, id); }
    return id;
  }

  /** All registered gradients, for `<defs>`. */
  defs(): string {
    let out = '';
    for (const [key, id] of this.ids) {
      out += `<radialGradient id="${id}"><stop offset="0" stop-color="rgb(${key})" stop-opacity="0.85"/><stop offset="1" stop-color="rgb(${key})" stop-opacity="0"/></radialGradient>`;
    }
    return out;
  }
}

/** Shared settings for SVG effects in one render. */
export interface EffectCtx {
  px: number;
  night: number;
  animate: boolean;
  glows: GlowPalette;
  shader: Shader;
}

/** A light pool: an ellipse of `rx` by `ry` metres in `color`; `flicker` animates its brightness. */
export function glowHalo(x: number, y: number, rx: number, ry: number, strength: number, color: RGB, fx: EffectCtx, flicker = false): string {
  const op = (v: number) => (v * strength).toFixed(2);
  const anim = flicker && fx.animate ? `<animate attributeName="opacity" values="${op(1)};${op(0.72)};${op(1.05)};${op(0.88)}" dur="${(1.7 + (Math.abs(x) % 1)).toFixed(2)}s" repeatCount="indefinite"/>` : '';
  return `<ellipse cx="${(x * fx.px).toFixed(1)}" cy="${(y * fx.px).toFixed(1)}" rx="${(rx * fx.px).toFixed(1)}" ry="${(ry * fx.px).toFixed(1)}" fill="url(#${fx.glows.id(color)})" opacity="${op(1)}">${anim}</ellipse>`;
}

function hexagon(r: number, from: number, count: number, closeAtCentre: boolean): string {
  const pts = Array.from({ length: count }, (_, k) => {
    const t = from + (k / 6) * Math.PI * 2;
    return `${(Math.cos(t) * r).toFixed(1)},${(Math.sin(t) * r * 0.9).toFixed(1)}`;
  });
  return pts.join(' ') + (closeAtCentre ? ' 0,0' : '');
}

function puffMotion(x: number, y: number, px: number, dur: number, begin: number): string {
  const p = (dx: number, dy: number) => `${((x + dx) * px).toFixed(1)} ${((y + dy) * px).toFixed(1)}`;
  return `<animateTransform attributeName="transform" type="translate" values="${p(0, 0)}; ${p(0.6, -1.6)}; ${p(1.4, -3.2)}" dur="${dur}s" begin="${begin.toFixed(2)}s" repeatCount="indefinite"/>`
    + `<animate attributeName="opacity" values="0.9;0.7;0" dur="${dur}s" begin="${begin.toFixed(2)}s" repeatCount="indefinite"/>`;
}

/** Faceted chimney smoke rising from a projected point. */
export function smokePuffs(x: number, y: number, puffs: number, fx: EffectCtx): string {
  const base: RGB = fx.shader.nightTint(hslToRgb(210, 8, 86), fx.night * 0.8);
  const shade: RGB = [base[0] * 0.86, base[1] * 0.88, base[2] * 0.92];
  const dur = 5.2;
  let out = '';
  for (let i = 0; i < puffs; i++) {
    const r = (0.16 + 0.05 * i) * fx.px;
    const body = `<polygon points="${hexagon(r, 0.3, 6, false)}" fill="${rgbCss(base)}"/><polygon points="${hexagon(r, 0.3 + Math.PI, 4, true)}" fill="${rgbCss(shade)}"/>`;
    out += fx.animate
      ? `<g>${body}${puffMotion(x, y, fx.px, dur, -(i * dur) / puffs)}</g>`
      : `<g transform="translate(${((x + 0.5 * i) * fx.px).toFixed(1)} ${((y - 1.1 * i) * fx.px).toFixed(1)})" opacity="${(0.85 - i * 0.18).toFixed(2)}">${body}</g>`;
  }
  return out;
}

/** Render one anchor's effect; returns [effect markup, glow markup]. A glow anchor may name its material in `data.mat`. */
export function anchorEffect(a: Anchor, x: number, y: number, fx: EffectCtx): [string, string] {
  if (a.kind === 'smoke') return [smokePuffs(x, y, Number(a.data?.puffs ?? 3), fx), ''];
  if (a.kind === 'glow' && fx.night > 0.05) {
    const r = Number(a.data?.r ?? 1);
    const color = (typeof a.data?.mat === 'string' ? fx.shader.litColor(a.data.mat) : undefined) ?? WARM;
    return ['', glowHalo(x, y, r, r * 0.78, 0.9 * fx.night, color, fx, true)];
  }
  return ['', ''];
}

/** The light colour used when a material defines none. */
export const DEFAULT_GLOW = WARM;
