/** A colour gradient as a 2D context creates it. */
export interface CanvasGradientLike { addColorStop(offset: number, color: string): void }

/** A fill rule for paths. */
export type FillRule = 'nonzero' | 'evenodd';

/**
 * The part of a 2D canvas context `renderCanvas` draws with. `CanvasRenderingContext2D` and
 * `OffscreenCanvasRenderingContext2D` both satisfy it, so rendering works on the main thread and in workers.
 */
export interface Canvas2D {
  globalAlpha: number;
  globalCompositeOperation: string;
  fillStyle: unknown;
  strokeStyle: unknown;
  lineWidth: number;
  lineJoin: string;
  lineCap: string;
  save(): void;
  restore(): void;
  transform(a: number, b: number, c: number, d: number, e: number, f: number): void;
  translate(x: number, y: number): void;
  scale(x: number, y: number): void;
  beginPath(): void;
  moveTo(x: number, y: number): void;
  lineTo(x: number, y: number): void;
  closePath(): void;
  arc(x: number, y: number, radius: number, startAngle: number, endAngle: number): void;
  rect(x: number, y: number, w: number, h: number): void;
  fill(fillRule?: FillRule): void;
  fill(path: object, fillRule?: FillRule): void;
  stroke(): void;
  stroke(path: object): void;
  createRadialGradient(x0: number, y0: number, r0: number, x1: number, y1: number, r1: number): CanvasGradientLike;
}

/** A constructor of paths from SVG path data, such as the global `Path2D`. */
export type Path2DCtor = new (d: string) => object;
