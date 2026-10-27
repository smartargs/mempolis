import { Z_SCALE } from './projection.ts';
import type { DisplayList } from './types.ts';

/** Screen-space rectangle in metres. */
export interface Bounds { x0: number; y0: number; x1: number; y1: number }

/** Screen-space bounds of a display list, optionally grown into `into`. */
export function boundsOf(dl: DisplayList, into?: Bounds): Bounds {
  const b = into ?? { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
  for (const f of dl.faces) for (const p of f.pts) {
    const x = p[0] - p[1], y = (p[0] + p[1]) * 0.5 - p[2] * Z_SCALE;
    b.x0 = Math.min(b.x0, x); b.y0 = Math.min(b.y0, y); b.x1 = Math.max(b.x1, x); b.y1 = Math.max(b.y1, y);
  }
  return b;
}

/** How a render frames its display list; shared by every renderer. */
export interface FrameOpts {
  /** Pixels per metre. */
  px?: number;
  /** Padding in metres. */
  pad?: number;
  /** Extra space above the bounds in metres, for smoke and glow; defaults to 3. */
  headroom?: number;
  /** Shared bounds so several renders line up at one scale. */
  bounds?: Bounds;
}

/**
 * The pixel window of a render: `x`, `y`, `w`, `h` is the drawn area in pixel units (the SVG viewBox, kept to a
 * tenth of a pixel) and `width` × `height` its natural size in whole pixels.
 */
export interface RenderFrame { px: number; x: number; y: number; w: number; h: number; width: number; height: number }

const tenth = (v: number) => Math.round(v * 10) / 10;

/** The frame `renderSVG` and `renderCanvas` draw a display list into, so both place every pixel alike. */
export function renderFrame(dl: DisplayList, o: FrameOpts = {}): RenderFrame {
  const px = o.px ?? 24, pad = o.pad ?? 0.6, headroom = o.headroom ?? 3, bb = o.bounds ?? boundsOf(dl);
  const w = (bb.x1 - bb.x0 + 2 * pad) * px, h = (bb.y1 - bb.y0 + 2 * pad + headroom) * px;
  return { px, x: tenth((bb.x0 - pad) * px), y: tenth((bb.y0 - pad - headroom) * px), w: tenth(w), h: tenth(h), width: Math.round(w), height: Math.round(h) };
}

/**
 * Scale and offset that fit a frame into a `width` × `height` pixel box, centred and uniformly scaled, as an SVG
 * viewBox is fitted into its image: pixel = frame unit × `s` + (`tx`, `ty`).
 */
export function frameFit(frame: RenderFrame, width = frame.width, height = frame.height): { s: number; tx: number; ty: number } {
  const s = Math.min(width / frame.w, height / frame.h);
  return { s, tx: (width - frame.w * s) / 2 - frame.x * s, ty: (height - frame.h * s) / 2 - frame.y * s };
}
