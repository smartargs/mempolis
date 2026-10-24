import type { Builder } from './builder.ts';
import type { Asset, AssetCtx, DecayRole } from './types.ts';

/** The decay an asset with `role` receives when its plot is at `decay`. */
export function decayFor(role: DecayRole, decay: number): number {
  return role === 'none' ? 0 : decay;
}

/** Build `asset` into `b`, honouring its decay role: earned assets neither decay nor fade. */
export function buildAsset<P>(b: Builder, asset: Asset<P>, params: P, ctx: AssetCtx) {
  const decay = decayFor(asset.decayRole, ctx.decay);
  b.scope({ fade: asset.decayRole === 'none' ? 0 : 1 }, () => asset.build(b, params, { ...ctx, decay }));
}
