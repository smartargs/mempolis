import { renderFrame, type FrameOpts } from './frame.ts';
import { logoDim, logoMatrix, paintScene, screenX, screenY, tenthText, type Halo, type SceneCtx } from './scene-paint.ts';
import { Shader } from './shade.ts';
import { GlowPalette, glowHalo, smokePuffs, type EffectCtx } from './svg-effects.ts';
import type { DisplayList, Face, LogoDef, Region, StyleDef } from './types.ts';

/** Options for `renderSVG`. */
export interface SvgOpts extends FrameOpts {
  /** 0 = day, 1 = night. */
  night?: number;
  /** 0..1 desaturation for neglected plots. */
  fade?: number;
  /** Strength of the night halos around lit faces, 1 by default; lower it for small figures whose lights should not bloom, 0 drops every halo. */
  glow?: number;
  /** Animate smoke and flicker with SMIL. */
  animate?: boolean;
  className?: string;
  /** Write width and height so the SVG renders at its natural pixel size. */
  size?: boolean;
}

let renderCount = 0;

function polygon(f: Face, fill: string, px: number): string {
  let pts = '';
  for (const p of f.pts) pts += `${pts ? ' ' : ''}${tenthText(screenX(p) * px)},${tenthText(screenY(p) * px)}`;
  return `<polygon points="${pts}" fill="${fill}" stroke="${fill}"${f.alpha != null ? ` opacity="${f.alpha}"` : ''}/>`;
}

function logoMarkup(f: Face, def: LogoDef, px: number, night: number): string {
  const m = logoMatrix(f, def, px), dim = logoDim(f, night);
  const opacity = f.alpha != null ? ` opacity="${f.alpha}"` : '';
  const shade = dim > 0.02 ? `<rect width="${def.w}" height="${def.h}" fill="rgb(6,8,16)" opacity="${dim.toFixed(2)}"/>` : '';
  return `<g transform="matrix(${m.map(v => v.toFixed(4)).join(' ')})" stroke-width="1" stroke-linejoin="miter"${opacity}>${def.svg}${shade}</g>`;
}

const screen = (markup: string) => `<g style="mix-blend-mode:screen">${markup}</g>`;

/** Render a display list to a standalone SVG string. */
export function renderSVG(dl: DisplayList, style: StyleDef, region: Region, o: SvgOpts = {}): string {
  const frame = renderFrame(dl, o), { px } = frame, night = o.night ?? 0;
  const shader = new Shader(style, region, { night, fade: o.fade });
  const fx: EffectCtx = { px, night, animate: !!o.animate, glows: new GlowPalette(`glow${++renderCount}`), shader };
  const sc: SceneCtx = { px, night, glow: o.glow ?? 1, shader, style };
  const halo = (h: Halo) => glowHalo(h.x, h.y, h.rx, h.ry, h.strength, h.color, fx, h.motion);
  let body = '';
  paintScene(dl, sc, {
    face: (f, fill) => { body += polygon(f, fill, px); },
    logo: (f, def) => { body += logoMarkup(f, def, px, night); },
    glow: h => { body += screen(halo(h)); },
    looseGlows: hs => { body += screen(hs.map(halo).join('')); },
    smoke: (x, y, puffs) => { body += smokePuffs(x, y, puffs, fx); },
  });
  const size = o.size ? ` width="${frame.width}" height="${frame.height}"` : '';
  const cls = o.className ? ` class="${o.className}"` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${frame.x} ${frame.y} ${frame.w} ${frame.h}"${size}${cls}>`
    + `<defs>${fx.glows.defs()}</defs><g stroke-width="0.6" stroke-linejoin="round">${body}</g></svg>`;
}
