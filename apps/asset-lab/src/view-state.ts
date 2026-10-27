import type { AssetCtx, Lod, Region } from '@mempolis/engine';

export interface ViewState {
  night: number;
  decay: number;
  rot: number;
  region: Region;
  lod: Lod;
  animate: boolean;
}

export const PX_PER_METRE: Record<Lod, number> = { 0: 26, 1: 13, 2: 6.5, 3: 3.2 };
export const LOD_NAMES: Record<Lod, string> = { 0: 'Visit', 1: 'Street', 2: 'Neighbourhood', 3: 'Town' };

export const initialState = (region: Region): ViewState => ({ night: 0, decay: 0, rot: 0, region, lod: 0, animate: true });

export const assetCtx = (s: ViewState, lod: Lod = s.lod): AssetCtx => ({ region: s.region, decay: s.decay, lod });

export const timeOfDayLabel = (night: number) => (night < 0.15 ? 'day' : night < 0.6 ? 'dusk' : 'night');
