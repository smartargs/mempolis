import type { Canvas2D, CanvasGradientLike, FillRule } from '../src/index.ts';

export type Call = [string, ...unknown[]];

export class RecordingPath {
  readonly d: string;
  constructor(d: string) { this.d = d; }
}

export class RecordingCanvas implements Canvas2D {
  readonly calls: Call[] = [];
  private state = { globalAlpha: 1, globalCompositeOperation: 'source-over', fillStyle: '#000' as unknown, strokeStyle: '#000' as unknown, lineWidth: 1, lineJoin: 'miter', lineCap: 'butt' };
  private readonly stack: (typeof this.state)[] = [];

  get globalAlpha() { return this.state.globalAlpha; }
  set globalAlpha(v) { this.state.globalAlpha = v; }
  get globalCompositeOperation() { return this.state.globalCompositeOperation; }
  set globalCompositeOperation(v) { this.state.globalCompositeOperation = v; }
  get fillStyle() { return this.state.fillStyle; }
  set fillStyle(v) { this.state.fillStyle = v; }
  get strokeStyle() { return this.state.strokeStyle; }
  set strokeStyle(v) { this.state.strokeStyle = v; }
  get lineWidth() { return this.state.lineWidth; }
  set lineWidth(v) { this.state.lineWidth = v; }
  get lineJoin() { return this.state.lineJoin; }
  set lineJoin(v) { this.state.lineJoin = v; }
  get lineCap() { return this.state.lineCap; }
  set lineCap(v) { this.state.lineCap = v; }

  save() { this.stack.push({ ...this.state }); this.calls.push(['save']); }
  restore() { this.state = this.stack.pop() ?? this.state; this.calls.push(['restore']); }
  transform(...m: number[]) { this.calls.push(['transform', ...m]); }
  translate(x: number, y: number) { this.calls.push(['translate', x, y]); }
  scale(x: number, y: number) { this.calls.push(['scale', x, y]); }
  beginPath() { this.calls.push(['beginPath']); }
  moveTo(x: number, y: number) { this.calls.push(['moveTo', x, y]); }
  lineTo(x: number, y: number) { this.calls.push(['lineTo', x, y]); }
  closePath() { this.calls.push(['closePath']); }
  arc(...a: number[]) { this.calls.push(['arc', ...a]); }
  rect(...a: number[]) { this.calls.push(['rect', ...a]); }
  fill(a?: object | FillRule, rule?: FillRule) {
    const path = typeof a === 'object' ? a : undefined;
    this.calls.push(['fill', { ...this.state, path, rule: typeof a === 'string' ? a : rule }]);
  }
  stroke(path?: object) { this.calls.push(['stroke', { ...this.state, path }]); }
  createRadialGradient(...a: number[]): CanvasGradientLike {
    const stops: [number, string][] = [];
    this.calls.push(['gradient', ...a, stops]);
    return { addColorStop: (o, c) => { stops.push([o, c]); } };
  }

  fills() { return this.calls.filter(c => c[0] === 'fill').map(c => c[1] as RecordingCanvas['state'] & { path?: object; rule?: FillRule }); }
  depth() { let d = 0; for (const [k] of this.calls) d += k === 'save' ? 1 : k === 'restore' ? -1 : 0; return d; }
}
