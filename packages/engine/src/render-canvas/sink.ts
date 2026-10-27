import { PUFF_BODY, PUFF_SHADE, hexagon, hundredth, logoDim, logoMatrix, screenX, screenY, smokeColors, smokeShapes, tenth, type Halo, type SceneCtx, type SceneSink } from '../scene-paint.ts';
import { rgbHex, type RGB } from '../shade.ts';
import type { Face, LogoDef } from '../types.ts';
import type { Canvas2D, CanvasGradientLike, Path2DCtor } from './context.ts';
import { logoShapes } from './logo-shapes.ts';

const TURN = Math.PI * 2;

function tracePolygon(ctx: Canvas2D, pts: [number, number][]) {
  ctx.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
  ctx.closePath();
}

export function canvasSink(ctx: Canvas2D, sc: SceneCtx, Path: Path2DCtor | undefined): SceneSink {
  const { px, night } = sc, gradients = new Map<string, CanvasGradientLike>();
  let alpha = NaN, style = '', smoke: [string, string] | undefined;
  const setAlpha = (a: number) => { if (a !== alpha) { ctx.globalAlpha = a; alpha = a; } };

  const gradient = (c: RGB) => {
    const key = `${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])}`;
    let g = gradients.get(key);
    if (!g) {
      g = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
      g.addColorStop(0, `rgba(${key},0.85)`);
      g.addColorStop(1, `rgba(${key},0)`);
      gradients.set(key, g);
    }
    return g;
  };

  const glow = (h: Halo) => {
    const op = hundredth(h.strength), rx = tenth(h.rx * px), ry = tenth(h.ry * px);
    if (op <= 0 || rx <= 0 || ry <= 0) return;
    ctx.save();
    ctx.globalCompositeOperation = 'screen';
    ctx.globalAlpha = op;
    ctx.translate(tenth(h.x * px), tenth(h.y * px));
    ctx.scale(rx, ry);
    ctx.fillStyle = gradient(h.color);
    ctx.beginPath();
    ctx.arc(0, 0, 1, 0, TURN);
    ctx.fill();
    ctx.restore();
  };

  const face = (f: Face, fill: string) => {
    const pts = f.pts;
    ctx.beginPath();
    ctx.moveTo(tenth(screenX(pts[0]) * px), tenth(screenY(pts[0]) * px));
    for (let i = 1; i < pts.length; i++) ctx.lineTo(tenth(screenX(pts[i]) * px), tenth(screenY(pts[i]) * px));
    ctx.closePath();
    if (fill !== style) { ctx.fillStyle = fill; ctx.strokeStyle = fill; style = fill; }
    setAlpha(f.alpha ?? 1);
    ctx.fill();
    ctx.stroke();
  };

  const logo = (f: Face, def: LogoDef) => {
    if (!Path) return;
    const a = f.alpha ?? 1, dim = logoDim(f, night);
    ctx.save();
    ctx.transform(...logoMatrix(f, def, px));
    for (const s of logoShapes(def, Path)) {
      if (s.fill) { ctx.globalAlpha = a * s.fillOpacity; ctx.fillStyle = s.fill; ctx.fill(s.path, s.rule); }
      if (s.stroke) {
        ctx.globalAlpha = a * s.strokeOpacity; ctx.strokeStyle = s.stroke;
        ctx.lineWidth = s.strokeWidth; ctx.lineCap = s.lineCap; ctx.lineJoin = s.lineJoin;
        ctx.stroke(s.path);
      }
    }
    if (dim > 0.02) {
      ctx.globalAlpha = a * hundredth(dim);
      ctx.fillStyle = 'rgb(6,8,16)';
      ctx.beginPath();
      ctx.rect(0, 0, def.w, def.h);
      ctx.fill();
    }
    ctx.restore();
  };

  const puffs = (x: number, y: number, count: number) => {
    const [base, shade] = smoke ??= smokeColors(sc.shader, night).map(rgbHex) as [string, string];
    for (const p of smokeShapes(x, y, count, px)) {
      const op = hundredth(p.opacity);
      if (op <= 0) continue;
      const body = hexagon(p.r, PUFF_BODY.from, PUFF_BODY.count, false), cut = hexagon(p.r, PUFF_SHADE.from, PUFF_SHADE.count, true);
      ctx.save();
      ctx.translate(tenth(p.x), tenth(p.y));
      ctx.globalAlpha = op;
      ctx.beginPath(); tracePolygon(ctx, body); tracePolygon(ctx, cut);
      ctx.fillStyle = base; ctx.fill('evenodd');
      ctx.beginPath(); tracePolygon(ctx, cut);
      ctx.fillStyle = shade; ctx.fill();
      ctx.restore();
    }
  };

  return { face, logo, glow, looseGlows: hs => { for (const h of hs) glow(h); }, smoke: puffs };
}
