import { Builder, boundsOf, buildAsset, hashSeed, renderSVG, type ArtPack, type Asset, type AssetCtx, type Bounds, type DisplayList, type Lod } from '@neoworld/engine';
import { LOD_NAMES, PX_PER_METRE, assetCtx, type ViewState } from './view-state.ts';

function buildStage(a: Asset, stage: number, ctx: AssetCtx, rot: number, extra: Record<string, unknown>): DisplayList {
  const b = new Builder({ seed: hashSeed(a.id, stage), rot });
  buildAsset(b, a, { ...a.stages[stage].params, ...extra }, ctx);
  return b.build();
}

function stageBackground(night: number): string {
  const day = getComputedStyle(document.documentElement).getPropertyValue('--card-day').trim() || '#e6ece2';
  return night > 0.02 ? `color-mix(in oklab, ${day}, #141b2c ${Math.round(night * 100)}%)` : day;
}

function svgFor(pack: ArtPack, dl: DisplayList, s: ViewState, lod: Lod, bounds?: Bounds): string {
  return renderSVG(dl, pack.style, s.region, { px: PX_PER_METRE[lod], night: s.night, fade: s.decay * 0.8, animate: s.animate, size: true, bounds });
}

/** Every stage of `a` side by side at one shared scale. */
export function renderStrip(host: HTMLElement, pack: ArtPack, a: Asset, s: ViewState, extra: Record<string, unknown>) {
  const lists = a.stages.map((_, i) => buildStage(a, i, assetCtx(s), s.rot, extra));
  const bounds: Bounds = { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
  for (const dl of lists) boundsOf(dl, bounds);
  host.replaceChildren(...lists.map((dl, i) => {
    const card = document.createElement('article');
    card.className = 'card';
    const st = a.stages[i];
    card.innerHTML = `<div class="meta"><b>${st.label}</b><span>${st.note ?? ''}</span></div>`
      + (st.means ? `<p class="means">${st.means}</p>` : '')
      + `<div class="stage" style="background:${stageBackground(s.night)}">${svgFor(pack, dl, s, s.lod, bounds)}</div>`;
    return card;
  }));
}

/** The final stage of `a` at every zoom level, at true on-screen size. */
export function renderZoomRow(host: HTMLElement, pack: ArtPack, a: Asset, s: ViewState, extra: Record<string, unknown>) {
  const last = a.stages.length - 1;
  host.replaceChildren(...([0, 1, 2, 3] as Lod[]).map(lod => {
    const dl = buildStage(a, last, assetCtx(s, lod), s.rot, extra);
    const fig = document.createElement('figure');
    fig.innerHTML = `<div class="well" style="background:${stageBackground(s.night)}">${svgFor(pack, dl, s, lod)}</div><figcaption>${LOD_NAMES[lod]} · ${dl.faces.length} faces</figcaption>`;
    return fig;
  }));
}
