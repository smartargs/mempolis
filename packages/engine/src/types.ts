import type { Vec3 } from './math.ts';

/** One flat, shaded polygon in world space. */
export interface Face {
  pts: Vec3[];
  /** Unit normal in world space, facing the camera. */
  n: Vec3;
  /** Semantic material name, resolved by the style (e.g. "wall", "roof", "leaf"). */
  mat: string;
  /** Shading-step offset for facet variety: -1, 0 or +1. */
  tone: number;
  depth: number;
  /** 0..1 light output for emissive materials (windows, lamps). Drawn untinted at night. */
  emit?: number;
  alpha?: number;
  tag?: string;
  /** Weight 0..1 of the render's fade for this face; earned objects use 0. */
  fade?: number;
}

/** A point the animation overlay attaches to: chimney tip, lamp, pond surface... */
export interface Anchor {
  kind: string;
  at: Vec3;
  data?: Record<string, number | string>;
}

export interface DisplayList {
  faces: Face[];
  anchors: Anchor[];
}

export type Region = 'mainland' | 'coast';
/** 0 visit, 1 street, 2 neighbourhood, 3 town. */
export type Lod = 0 | 1 | 2 | 3;

export interface Material {
  h: number;
  s: number;
  l: number;
  /** Lit colour for emissive materials, as HSL. */
  lit?: [number, number, number];
}

export interface StyleDef {
  id: string;
  /** Shading steps per material (quantised lighting). */
  steps: number;
  /** Direction toward the light. */
  light: Vec3;
  ambient: number;
  materials(region: Region): Record<string, Material>;
  night: { tint: [number, number, number]; lift: [number, number, number] };
}

export interface AssetCtx {
  region: Region;
  /** 0 = fully active, 1 = deep sleep. */
  decay: number;
  lod: Lod;
}

/**
 * How an asset responds to inactivity: `activity` shows recent use (lawn, smoke, lamps),
 * `maintained` gets lightly neglected (house, path, fence), `none` never decays (earned or proven things).
 */
export type DecayRole = 'activity' | 'maintained' | 'none';

/** Who can see an element. */
export type Visibility = 'public' | 'after claim' | 'owner only';
/** When an element changes. */
export type Cadence = 'weekly' | 'live' | 'clock';

/** What an asset represents: the on-chain data behind it and how it is shown. */
export interface AssetMapping {
  /** The on-chain data that drives the asset. */
  source: string;
  visibility: Visibility;
  cadence: Cadence[];
}

/** One choice on a variant axis; its params are merged into every stage. */
export interface VariantOption<P> {
  label: string;
  params: Partial<P>;
  means?: string;
}

/** An axis that applies across all stages (heritage, species, look), not a growth step. */
export interface AssetVariant<P> {
  name: string;
  options: VariantOption<P>[];
}

export interface AssetStage<P> {
  label: string;
  params: P;
  note?: string;
  /** The on-chain condition this stage stands for. */
  means?: string;
}

export interface Asset<P = any> {
  id: string;
  name: string;
  batch: number;
  /** What drives it, from the World Catalog. */
  driver: string;
  decayRole: DecayRole;
  mapping: AssetMapping;
  /** Regions this asset belongs to; omitted means both. */
  regions?: Region[];
  /** Footprint in metres (local x by local y), centred on the origin. */
  footprint: [number, number];
  stages: AssetStage<P>[];
  variants?: AssetVariant<P>[];
  build(b: import('./builder.ts').Builder, p: P, ctx: AssetCtx): void;
}

/** Which asset draws a catalog element in each region. */
export type ElementMap = Record<string, Partial<Record<Region, string>>>;

export interface ArtPack {
  id: string;
  name: string;
  style: StyleDef;
  assets: Asset[];
  /** Catalog elements (house, holding, activity...) mapped to their asset per region. */
  elements?: ElementMap;
}
