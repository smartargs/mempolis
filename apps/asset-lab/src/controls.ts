import type { Lod, Region } from '@neoworld/engine';
import { timeOfDayLabel, type ViewState } from './view-state.ts';

const byId = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const slider = (id: string) => byId<HTMLInputElement>(id);

function pressOnly(selector: string, active: Element) {
  document.querySelectorAll(selector).forEach(x => x.setAttribute('aria-pressed', String(x === active)));
}

function readSliders(s: ViewState) {
  s.night = +slider('night').value / 100;
  s.decay = +slider('decay').value / 100;
  s.rot = (+slider('rot').value * Math.PI) / 180;
  s.animate = slider('anim').checked;
  byId('nightV').textContent = timeOfDayLabel(s.night);
  byId('decayV').textContent = `${slider('decay').value}%`;
  byId('rotV').textContent = `${slider('rot').value}°`;
}

function applyUrl(s: ViewState) {
  const q = new URLSearchParams(location.search);
  for (const id of ['night', 'decay', 'rot']) if (q.has(id)) slider(id).value = q.get(id)!;
  if (q.get('anim') === '0') slider('anim').checked = false;
  if (q.get('region') === 'coast') s.region = 'coast';
  if (q.has('lod')) s.lod = Number(q.get('lod')) as Lod;
  document.querySelectorAll('[data-region]').forEach(b => b.setAttribute('aria-pressed', String((b as HTMLElement).dataset.region === s.region)));
  document.querySelectorAll('[data-lod]').forEach(b => b.setAttribute('aria-pressed', String(Number((b as HTMLElement).dataset.lod) === s.lod)));
}

/** Lock the region toggle to the asset's only region, or unlock it. */
export function lockRegion(state: ViewState, regions: Region[] | undefined) {
  const only = regions?.length === 1 ? regions[0] : null;
  if (only) state.region = only;
  document.querySelectorAll<HTMLButtonElement>('[data-region]').forEach(b => {
    b.disabled = !!only && b.dataset.region !== only;
    b.setAttribute('aria-pressed', String(b.dataset.region === state.region));
  });
}

/** Enable or disable the decay control for assets that never decay. */
export function setDecayEnabled(enabled: boolean) {
  slider('decay').disabled = !enabled;
  slider('decay').closest('label')?.classList.toggle('off', !enabled);
}

/** Wire the control strip to `state`; `onChange` runs after every change. */
export function bindControls(state: ViewState, onChange: () => void) {
  applyUrl(state);
  readSliders(state);
  for (const id of ['night', 'decay', 'rot', 'anim']) slider(id).addEventListener('input', () => { readSliders(state); onChange(); });
  document.querySelectorAll<HTMLButtonElement>('[data-region]').forEach(btn => btn.addEventListener('click', () => {
    state.region = btn.dataset.region as Region; pressOnly('[data-region]', btn); onChange();
  }));
  document.querySelectorAll<HTMLButtonElement>('[data-lod]').forEach(btn => btn.addEventListener('click', () => {
    state.lod = Number(btn.dataset.lod) as Lod; pressOnly('[data-lod]', btn); onChange();
  }));
}
