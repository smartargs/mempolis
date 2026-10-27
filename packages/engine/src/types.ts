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
  /** Id of the part this face was built under (see `Builder.part`). */
  part?: number;
  /** Id of a logo in the style, drawn across this quad instead of a fill (see `Builder.logo`). */
  logo?: string;
}

/** A point the animation overlay attaches to: chimney tip, lamp, pond surface... */
export interface Anchor {
  kind: string;
  at: Vec3;
  data?: Record<string, number | string>;
  /** Id of the part this anchor was built under (see `Builder.part`). */
  part?: number;
  /** How many faces of the display list paint before this anchor's effect, so nearer faces cover it. */
  after?: number;
}

export interface DisplayList {
  faces: Face[];
  anchors: Anchor[];
}

/**
 * The region ids art packs draw, one per city. The engine lists none itself: a pack adds its own by augmenting this
 * interface, `declare module '@mempolis/engine' { interface RegionIds { n3: true } }`.
 */
export interface RegionIds {}
/** A region id registered in `RegionIds`. */
export type Region = Extract<keyof RegionIds, string>;
/** A city an art pack draws: its region id and the name tools list it under. */
export interface RegionDef { id: Region; name: string }
/** 0 visit, 1 street, 2 neighbourhood, 3 town. */
export type Lod = 0 | 1 | 2 | 3;

export interface Material {
  h: number;
  s: number;
  l: number;
  /** Lit colour for emissive materials, as HSL. */
  lit?: [number, number, number];
}

/**
 * A flat brand mark: inner SVG markup in a `w` × `h` box, with flat fills only (no ids, gradients, text or links).
 * `glow` (HSL) is the colour a lit sign bleeds at night; without it a lit logo casts no halo.
 */
export interface LogoDef { w: number; h: number; svg: string; glow?: [number, number, number] }

export interface StyleDef {
  id: string;
  /** Shading steps per material (quantised lighting). */
  steps: number;
  /** Direction toward the light. */
  light: Vec3;
  ambient: number;
  materials(region: Region): Record<string, Material>;
  night: { tint: [number, number, number]; lift: [number, number, number] };
  /** Brand marks that faces can show, by id. */
  logos?: Record<string, LogoDef>;
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

/** A looping animation; each frame is rebuilt with `anim` set to its name and `phase` running from 0 to 1. */
export interface AssetAnimation {
  name: string;
  frames: number;
  fps: number;
}

/** Parameters every animated asset accepts. */
export interface AnimatedParams {
  anim?: string;
  /** Position in the loop, 0 inclusive to 1 exclusive. */
  phase?: number;
}

export interface AssetStage<P> {
  label: string;
  params: P;
  note?: string;
  /** The on-chain condition this stage stands for. */
  means?: string;
}

/** Which asset draws a catalog element in each region. */
export type ElementMap = Record<string, Partial<Record<Region, string>>>;
