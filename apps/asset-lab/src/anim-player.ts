import { Builder, boundsOf, buildAsset, hashSeed, renderSVG, type ArtPack, type Asset, type AssetAnimation, type Bounds } from '@mempolis/engine';
import { assetCtx, type ViewState } from './view-state.ts';

const PX = 70;
let timer = 0;
const chosen = new Map<string, string>();
let playing = true;

function stop() {
  if (timer) cancelAnimationFrame(timer);
  timer = 0;
}

function renderFrames(pack: ArtPack, a: Asset, anim: AssetAnimation, s: ViewState, extra: Record<string, unknown>): string[] {
  const base = { ...a.stages[0].params, ...extra, anim: anim.name };
  const lists = Array.from({ length: anim.frames }, (_, i) => {
    const b = new Builder({ seed: hashSeed(a.id, 'anim'), rot: s.rot });
    buildAsset(b, a, { ...base, phase: i / anim.frames }, assetCtx(s, 0));
    return b.build();
  });
  const bounds: Bounds = { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
  for (const dl of lists) boundsOf(dl, bounds);
  return lists.map(dl => renderSVG(dl, pack.style, s.region, { px: PX, night: s.night, fade: s.decay * 0.8, bounds, size: true }));
}

function loop(stage: HTMLElement, frames: string[], fps: number) {
  stop();
  let start = performance.now(), last = -1;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const tick = (now: number) => {
    const i = playing && !reduced && !document.hidden ? Math.floor(((now - start) / 1000) * fps) % frames.length : Math.max(0, last);
    if (i !== last) { stage.innerHTML = frames[i]; last = i; }
    timer = requestAnimationFrame(tick);
  };
  timer = requestAnimationFrame(tick);
}

/** Show a looping animation player for assets that declare animations; hides itself otherwise. */
export function renderPlayer(host: HTMLElement, pack: ArtPack, a: Asset, s: ViewState, extra: Record<string, unknown>, background: string) {
  stop();
  const anims = a.animations ?? [];
  host.hidden = anims.length === 0 || a.stages.length === 0;
  if (host.hidden) return;
  const current = anims.find(x => x.name === chosen.get(a.id)) ?? anims[0];
  const row = document.createElement('div');
  row.className = 'row';
  const seg = document.createElement('div');
  seg.className = 'seg'; seg.setAttribute('role', 'group'); seg.setAttribute('aria-label', 'Animation');
  for (const anim of anims) {
    const btn = document.createElement('button');
    btn.textContent = anim.name; btn.setAttribute('aria-pressed', String(anim === current));
    btn.addEventListener('click', () => { chosen.set(a.id, anim.name); renderPlayer(host, pack, a, s, extra, background); });
    seg.append(btn);
  }
  const play = document.createElement('button');
  play.className = 'seg'; play.textContent = playing ? 'Pause' : 'Play';
  play.addEventListener('click', () => { playing = !playing; play.textContent = playing ? 'Pause' : 'Play'; });
  const info = document.createElement('span');
  info.className = 'fps'; info.textContent = `${current.frames} frames · ${current.fps} fps`;
  row.append(seg, play, info);
  const stage = document.createElement('div');
  stage.className = 'stage'; stage.style.background = background;
  host.replaceChildren(row, stage);
  loop(stage, renderFrames(pack, a, current, s, extra), current.fps);
}
