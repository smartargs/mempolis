import { project } from './projection.ts';
import { Shader, rgbCss } from './shade.ts';
import { DEFAULT_GLOW, GlowPalette, anchorEffect, glowHalo, type EffectCtx } from './svg-effects.ts';
import type { DisplayList, Face, LogoDef, Region, StyleDef } from './types.ts';

/** Screen-space rectangle in metres. */
export interface Bounds { x0: number; y0: number; x1: number; y1: number }

/** Screen-space bounds of a display list, optionally grown into `into`. */
export function boundsOf(dl: DisplayList, into?: Bounds): Bounds {
  const b = into ?? { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
  for (const f of dl.faces) for (const p of f.pts) {
    const [x, y] = project(p);
    b.x0 = Math.min(b.x0, x); b.y0 = Math.min(b.y0, y); b.x1 = Math.max(b.x1, x); b.y1 = Math.max(b.y1, y);
  }
  return b;
}

/** Options for `renderSVG`. */
export interface SvgOpts {
  /** Pixels per metre. */
  px?: number;
  /** 0 = day, 1 = night. */
  night?: number;
  /** 0..1 desaturation for neglected plots. */
  fade?: number;
  /** Padding in metres. */
  pad?: number;
  /** Extra space above the bounds in metres, for smoke and glow; defaults to 3. */
  headroom?: number;
  /** Shared bounds so several renders line up at one scale. */
  bounds?: Bounds;
  /** Animate smoke and flicker with SMIL. */
  animate?: boolean;
  className?: string;
  /** Write width and height so the SVG renders at its natural pixel size. */
  size?: boolean;
}

let renderCount = 0;

function polygon(f: Face, fill: string, px: number): string {
  const pts = f.pts.map(p => { const [x, y] = project(p); return `${(x * px).toFixed(1)},${(y * px).toFixed(1)}`; }).join(' ');
  return `<polygon points="${pts}" fill="${fill}" stroke="${fill}" stroke-width="0.6" stroke-linejoin="round"${f.alpha != null ? ` opacity="${f.alpha}"` : ''}/>`;
}

function emissiveHalo(f: Face, fx: EffectCtx): string {
  let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
  for (const p of f.pts) { const [x, y] = project(p); x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  const color = fx.shader.litColor(f.mat) ?? DEFAULT_GLOW;
  return glowHalo((x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2 + 0.35, (y1 - y0) / 2 + 0.3, (f.emit ?? 0) * fx.night * 0.85, color, fx);
}

const LOGO_NIGHT_DIM = 0.5;

function logoMarkup(f: Face, def: LogoDef | undefined, px: number, night: number): string {
  if (!def || f.pts.length < 4) return '';
  const [a, b, , d] = f.pts.map(p => project(p));
  const m = [(b[0] - a[0]) * px / def.w, (b[1] - a[1]) * px / def.w, (d[0] - a[0]) * px / def.h, (d[1] - a[1]) * px / def.h, a[0] * px, a[1] * px];
  const opacity = f.alpha != null ? ` opacity="${f.alpha}"` : '';
  const dim = night * LOGO_NIGHT_DIM * (1 - (f.emit ?? 0));
  const shade = dim > 0.02 ? `<rect width="${def.w}" height="${def.h}" fill="rgb(6,8,16)" opacity="${dim.toFixed(2)}"/>` : '';
  return `<g transform="matrix(${m.map(v => v.toFixed(4)).join(' ')})"${opacity}>${def.svg}${shade}</g>`;
}

/** Render a display list to a standalone SVG string. */
export function renderSVG(dl: DisplayList, style: StyleDef, region: Region, o: SvgOpts = {}): string {
  const px = o.px ?? 24, pad = o.pad ?? 0.6, headroom = o.headroom ?? 3, night = o.night ?? 0;
  const bb = o.bounds ?? boundsOf(dl);
  const shader = new Shader(style, region, { night, fade: o.fade });
  const fx: EffectCtx = { px, night, animate: !!o.animate, glows: new GlowPalette(`glow${++renderCount}`), shader };
  const faces: string[] = [], halos: string[] = [], effects: string[] = [];
  for (const f of dl.faces) {
    faces.push(f.logo ? logoMarkup(f, style.logos?.[f.logo], px, night) : polygon(f, rgbCss(shader.color(f)), px));
    if (f.emit && night > 0.05) halos.push(emissiveHalo(f, fx));
  }
  for (const a of dl.anchors) {
    const [x, y] = project(a.at);
    const [effect, glow] = anchorEffect(a, x, y, fx);
    effects.push(effect); halos.push(glow);
  }
  const x0 = (bb.x0 - pad) * px, y0 = (bb.y0 - pad - headroom) * px, w = (bb.x1 - bb.x0 + 2 * pad) * px, h = (bb.y1 - bb.y0 + 2 * pad + headroom) * px;
  const size = o.size ? ` width="${w.toFixed(0)}" height="${h.toFixed(0)}"` : '';
  const cls = o.className ? ` class="${o.className}"` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${x0.toFixed(1)} ${y0.toFixed(1)} ${w.toFixed(1)} ${h.toFixed(1)}"${size}${cls}>`
    + `<defs>${fx.glows.defs()}</defs>${faces.join('')}<g style="mix-blend-mode:screen">${halos.join('')}</g>${effects.join('')}</svg>`;
}
