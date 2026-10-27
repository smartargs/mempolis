import type { LogoDef } from '../types.ts';
import type { FillRule, Path2DCtor } from './context.ts';

export interface LogoShape {
  path: object;
  fill?: string;
  rule: FillRule;
  stroke?: string;
  strokeWidth: number;
  lineCap: string;
  lineJoin: string;
  fillOpacity: number;
  strokeOpacity: number;
}

const TAG = /<(path|rect|circle|ellipse)\b([^>]*?)\/?>/g;
const ATTR = /([\w:-]+)\s*=\s*"([^"]*)"/g;

const num = (a: Record<string, string>, k: string, fallback = 0) => (a[k] !== undefined ? Number.parseFloat(a[k]) : fallback);

function roundedRect(x: number, y: number, w: number, h: number, rx: number, ry: number): string {
  if (rx <= 0 || ry <= 0) return `M${x} ${y}h${w}v${h}h${-w}z`;
  return `M${x + rx} ${y}h${w - 2 * rx}a${rx} ${ry} 0 0 1 ${rx} ${ry}v${h - 2 * ry}a${rx} ${ry} 0 0 1 ${-rx} ${ry}`
    + `h${2 * rx - w}a${rx} ${ry} 0 0 1 ${-rx} ${-ry}v${2 * ry - h}a${rx} ${ry} 0 0 1 ${rx} ${-ry}z`;
}

const ellipse = (cx: number, cy: number, rx: number, ry: number) =>
  `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${2 * rx} 0a${rx} ${ry} 0 1 0 ${-2 * rx} 0z`;

function pathData(tag: string, a: Record<string, string>): string | undefined {
  if (tag === 'path') return a.d;
  if (tag === 'circle') return ellipse(num(a, 'cx'), num(a, 'cy'), num(a, 'r'), num(a, 'r'));
  if (tag === 'ellipse') return ellipse(num(a, 'cx'), num(a, 'cy'), num(a, 'rx'), num(a, 'ry'));
  const w = num(a, 'width'), h = num(a, 'height');
  const rx = Math.min(num(a, 'rx', num(a, 'ry')), w / 2), ry = Math.min(num(a, 'ry', num(a, 'rx')), h / 2);
  return roundedRect(num(a, 'x'), num(a, 'y'), w, h, rx, ry);
}

const paint = (v: string | undefined, fallback: string | undefined) => (v === undefined ? fallback : v === 'none' ? undefined : v);

function parseLogo(svg: string, Path: Path2DCtor): LogoShape[] {
  const out: LogoShape[] = [];
  for (const [, tag, attrs] of svg.matchAll(TAG)) {
    const a: Record<string, string> = {};
    for (const [, k, v] of attrs.matchAll(ATTR)) a[k] = v;
    const d = pathData(tag, a);
    if (!d) continue;
    const opacity = num(a, 'opacity', 1);
    out.push({
      path: new Path(d), fill: paint(a.fill, '#000'), rule: a['fill-rule'] === 'evenodd' ? 'evenodd' : 'nonzero', stroke: paint(a.stroke, undefined),
      strokeWidth: num(a, 'stroke-width', 1), lineCap: a['stroke-linecap'] ?? 'butt', lineJoin: a['stroke-linejoin'] ?? 'miter',
      fillOpacity: opacity * num(a, 'fill-opacity', 1), strokeOpacity: opacity * num(a, 'stroke-opacity', 1),
    });
  }
  return out;
}

const cache = new WeakMap<Path2DCtor, WeakMap<LogoDef, LogoShape[]>>();

export function logoShapes(def: LogoDef, Path: Path2DCtor): LogoShape[] {
  let byLogo = cache.get(Path);
  if (!byLogo) { byLogo = new WeakMap(); cache.set(Path, byLogo); }
  let shapes = byLogo.get(def);
  if (!shapes) { shapes = parseLogo(def.svg, Path); byLogo.set(def, shapes); }
  return shapes;
}
