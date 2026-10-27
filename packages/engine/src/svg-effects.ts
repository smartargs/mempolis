import { PUFF_BODY, PUFF_SHADE, WARM, hexagon, smokeColors, smokeShapes, tenth, type GlowMotion } from './scene-paint.ts';
import { rgbHex, type RGB, type Shader } from './shade.ts';

export type { GlowMotion } from './scene-paint.ts';

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

function glowAnimation(motion: GlowMotion, strength: number, phase: number, animate: boolean): string {
  if (!animate || motion === 'steady') return '';
  const op = (v: number) => (v * strength).toFixed(2);
  if (motion === 'blink') return `<animate attributeName="opacity" values="${op(1)};${op(0.05)}" calcMode="discrete" dur="${(1.2 + phase * 0.6).toFixed(2)}s" repeatCount="indefinite"/>`;
  return `<animate attributeName="opacity" values="${op(1)};${op(0.72)};${op(1.05)};${op(0.88)}" dur="${(1.7 + phase).toFixed(2)}s" repeatCount="indefinite"/>`;
}

/** A light pool: an ellipse of `rx` by `ry` metres in `color`, steady, flickering or blinking. */
export function glowHalo(x: number, y: number, rx: number, ry: number, strength: number, color: RGB, fx: EffectCtx, motion: GlowMotion = 'steady'): string {
  const anim = glowAnimation(motion, strength, Math.abs(x) % 1, fx.animate);
  return `<ellipse cx="${tenth(x * fx.px)}" cy="${tenth(y * fx.px)}" rx="${tenth(rx * fx.px)}" ry="${tenth(ry * fx.px)}" fill="url(#${fx.glows.id(color)})" opacity="${strength.toFixed(2)}">${anim}</ellipse>`;
}

const points = (pts: [number, number][]) => pts.map(([x, y]) => `${x},${y}`).join(' ');

function puffMotion(x: number, y: number, px: number, dur: number, begin: number): string {
  const p = (dx: number, dy: number) => `${tenth((x + dx) * px)} ${tenth((y + dy) * px)}`;
  return `<animateTransform attributeName="transform" type="translate" values="${p(0, 0)}; ${p(0.6, -1.6)}; ${p(1.4, -3.2)}" dur="${dur}s" begin="${begin.toFixed(2)}s" repeatCount="indefinite"/>`
    + `<animate attributeName="opacity" values="0.9;0.7;0" dur="${dur}s" begin="${begin.toFixed(2)}s" repeatCount="indefinite"/>`;
}

/** Faceted chimney smoke rising from a projected point. */
export function smokePuffs(x: number, y: number, puffs: number, fx: EffectCtx): string {
  const [base, shade] = smokeColors(fx.shader, fx.night);
  const dur = 5.2;
  let out = '';
  smokeShapes(x, y, puffs, fx.px).forEach((puff, i) => {
    const body = `<polygon points="${points(hexagon(puff.r, PUFF_BODY.from, PUFF_BODY.count, false))}" fill="${rgbHex(base)}"/><polygon points="${points(hexagon(puff.r, PUFF_SHADE.from, PUFF_SHADE.count, true))}" fill="${rgbHex(shade)}"/>`;
    out += fx.animate
      ? `<g>${body}${puffMotion(x, y, fx.px, dur, -(i * dur) / puffs)}</g>`
      : `<g transform="translate(${tenth(puff.x)} ${tenth(puff.y)})" opacity="${puff.opacity.toFixed(2)}">${body}</g>`;
  });
  return out;
}

/** The light colour used when a material defines none. */
export const DEFAULT_GLOW = WARM;
