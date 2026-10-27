import { frameFit, renderFrame, type FrameOpts, type RenderFrame } from '../frame.ts';
import { paintScene } from '../scene-paint.ts';
import { Shader } from '../shade.ts';
import type { DisplayList, Region, StyleDef } from '../types.ts';
import type { Canvas2D, Path2DCtor } from './context.ts';
import { canvasSink } from './sink.ts';

export type { Canvas2D, CanvasGradientLike, FillRule, Path2DCtor } from './context.ts';

/** Options for `renderCanvas`. */
export interface CanvasOpts extends FrameOpts {
  /** 0 = day, 1 = night. */
  night?: number;
  /** 0..1 desaturation for neglected plots. */
  fade?: number;
  /** Strength of the night halos around lit faces, 1 by default; 0 drops every halo. */
  glow?: number;
  /** A frame from `renderFrame`, to skip measuring the display list again. */
  frame?: RenderFrame;
  /** Pixel box the frame is fitted into; defaults to the frame's natural size. */
  width?: number;
  height?: number;
  /** Builds logo paths from SVG path data; defaults to the global `Path2D`. Without one, logos are skipped. */
  path2d?: Path2DCtor;
}

/**
 * Draw a display list onto a 2D canvas context exactly where `renderSVG` would place it: same frame, paint order,
 * quantised colours, logos, night halos (screen-blended in place) and still smoke. Draws through the context's
 * current transform and leaves its state as it found it. Returns the frame, whose `width` × `height` is the
 * natural canvas size.
 */
export function renderCanvas(ctx: Canvas2D, dl: DisplayList, style: StyleDef, region: Region, o: CanvasOpts = {}): RenderFrame {
  const frame = o.frame ?? renderFrame(dl, o), fit = frameFit(frame, o.width, o.height), night = o.night ?? 0;
  const shader = new Shader(style, region, { night, fade: o.fade });
  const sc = { px: frame.px, night, glow: o.glow ?? 1, shader, style };
  const Path = o.path2d ?? (globalThis as { Path2D?: Path2DCtor }).Path2D;
  ctx.save();
  ctx.transform(fit.s, 0, 0, fit.s, fit.tx, fit.ty);
  ctx.globalCompositeOperation = 'source-over';
  ctx.lineWidth = 0.6;
  ctx.lineJoin = 'round';
  paintScene(dl, sc, canvasSink(ctx, sc, Path));
  ctx.restore();
  return frame;
}
