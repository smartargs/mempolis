import type { Builder } from './builder.ts';
import type { AssetAnimation, AssetCtx, AssetMapping, AssetStage, AssetVariant, DecayRole, ElementMap, Region, RegionDef, StyleDef } from './types.ts';

/** One drawable thing of an art pack, with its growth stages and how it builds into a `Builder`. */
export interface Asset<P = any> {
  id: string;
  name: string;
  batch: number;
  /** What drives it, from the World Catalog. */
  driver: string;
  decayRole: DecayRole;
  mapping: AssetMapping;
  /** Regions this asset belongs to; omitted means every region of its pack. */
  regions?: Region[];
  /** Where the asset lab lists it, such as "Landmarks"; omitted lists it by its regions. */
  group?: string;
  /** Footprint in metres (local x by local y), centred on the origin. */
  footprint: [number, number];
  stages: AssetStage<P>[];
  variants?: AssetVariant<P>[];
  animations?: AssetAnimation[];
  build(b: Builder, p: P, ctx: AssetCtx): void;
}

/** A style plus the assets drawn with it; packs are interchangeable. */
export interface ArtPack {
  id: string;
  name: string;
  style: StyleDef;
  /** The cities this pack draws, in the order tools offer them. */
  regions: RegionDef[];
  assets: Asset[];
  /** Catalog elements (house, holding, activity...) mapped to their asset per region. */
  elements?: ElementMap;
}
