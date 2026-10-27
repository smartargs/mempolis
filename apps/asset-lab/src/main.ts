import pack from 'virtual:art-pack';
import { markSelected, renderNav, selectedAsset } from './asset-nav.ts';
import { bindControls, lockRegion, setDecayEnabled } from './controls.ts';
import { renderPlayer } from './anim-player.ts';
import { renderMapping } from './mapping-panel.ts';
import { renderVariants, variantParams } from './variant-picker.ts';
import { renderStrip, renderZoomRow, stageBackground } from './stage-views.ts';
import { initialState } from './view-state.ts';

const byId = (id: string) => document.getElementById(id) as HTMLElement;
const state = initialState(pack.regions[0].id);

function render() {
  const asset = selectedAsset(pack);
  markSelected(byId('list'), asset.id);
  byId('title').textContent = asset.name;
  byId('driver').textContent = asset.driver;
  renderMapping(byId('mapping'), asset);
  setDecayEnabled(asset.decayRole !== 'none');
  lockRegion(state, asset.regions);
  if (asset.stages.length === 0) {
    byId('strip').textContent = 'No stages yet.';
    byId('zooms').replaceChildren();
    byId('variants').hidden = true;
    byId('player').hidden = true;
    return;
  }
  renderVariants(byId('variants'), asset, render);
  const extra = variantParams(asset);
  renderPlayer(byId('player'), pack, asset, state, extra, stageBackground(state.night));
  renderStrip(byId('strip'), pack, asset, state, extra);
  renderZoomRow(byId('zooms'), pack, asset, state, extra);
}

byId('pack').textContent = pack.name;
renderNav(byId('list'), pack);
bindControls(state, pack.regions, render);
window.addEventListener('hashchange', render);
render();
